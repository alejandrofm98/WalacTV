package com.example.walactv.ui.compose

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.walactv.data.model.AppUpdateAvailability
import com.example.walactv.data.model.AppUpdateInfo
import com.example.walactv.ui.fragment.ComposeMainFragment
import com.example.walactv.data.model.evaluateAppUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

internal fun ComposeMainFragment.restoreCachedUpdateState() {
    val installed = installedAppVersion ?: return
    val cachedUpdate = appUpdateRepository.loadCachedUpdate() ?: return
    availableUpdate = cachedUpdate
    when (evaluateAppUpdate(installed, cachedUpdate)) {
        AppUpdateAvailability.REQUIRED -> {
            mandatoryUpdate = cachedUpdate
            updateStatusMessage = "Actualizacion obligatoria disponible"
            startUpdateFlowIfReady()
        }
        AppUpdateAvailability.UP_TO_DATE -> {
            mandatoryUpdate = null
            updateStatusMessage = "Aplicacion actualizada"
        }
        AppUpdateAvailability.OPTIONAL -> {
            mandatoryUpdate = cachedUpdate
            updateStatusMessage = "Actualizacion obligatoria disponible"
            startUpdateFlowIfReady()
        }
    }
}

internal fun ComposeMainFragment.checkForAppUpdates(showToast: Boolean = false) {
    if (hasCheckedForUpdates || isUpdateDownloading) return
    hasCheckedForUpdates = true

    scope.launch {
        isCheckingUpdates = true
        updateErrorMessage = null

        val remoteUpdate = runCatching { appUpdateRepository.fetchRemoteUpdate() }.getOrNull()
        if (remoteUpdate == null) {
            updateStatusMessage = if (mandatoryUpdate != null) "Actualizacion obligatoria pendiente" else "No se pudo comprobar"
            if (showToast) Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_LONG).show()
            isCheckingUpdates = false
            if (mandatoryUpdate != null) startUpdateFlowIfReady()
            return@launch
        }

        appUpdateRepository.cacheUpdate(remoteUpdate)
        availableUpdate = remoteUpdate

        when (evaluateAppUpdate(installedAppVersion ?: return@launch, remoteUpdate)) {
            AppUpdateAvailability.REQUIRED -> {
                mandatoryUpdate = remoteUpdate
                updateStatusMessage = "Actualizacion obligatoria disponible"
                startUpdateFlowIfReady()
            }
            AppUpdateAvailability.UP_TO_DATE -> {
                mandatoryUpdate = null
                updateStatusMessage = "Aplicacion actualizada"
            }
            AppUpdateAvailability.OPTIONAL -> {
                mandatoryUpdate = remoteUpdate
                updateStatusMessage = "Actualizacion obligatoria disponible"
                startUpdateFlowIfReady()
            }
        }
        if (showToast) Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_SHORT).show()
        isCheckingUpdates = false
    }
}

internal fun ComposeMainFragment.startUpdateFlowIfReady() {
    val updateInfo = mandatoryUpdate ?: availableUpdate ?: return
    if (isUpdateDownloading) return
    startUpdateFlow(updateInfo)
}

@SuppressLint("QueryPermissionsNeeded")
internal fun ComposeMainFragment.startUpdateFlow(updateInfo: AppUpdateInfo? = null) {
    val info = updateInfo ?: mandatoryUpdate ?: availableUpdate ?: return
    if (!canRequestPackageInstalls()) {
        pendingInstallPermission = true
        updateStatusMessage = "Permite instalar apps desconocidas para continuar"
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${requireContext().packageName}".toUri())
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            runCatching { startActivity(intent) }.onFailure { updateErrorMessage = "No se pudo abrir la configuracion de instalacion" }
        } else {
            updateErrorMessage = "Activa manualmente la instalacion desde origenes desconocidos para WalacTV"
        }
        return
    }
    startUpdateDownload(info)
}

internal fun ComposeMainFragment.canRequestPackageInstalls(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            requireContext().packageManager.canRequestPackageInstalls()

internal fun ComposeMainFragment.startUpdateDownload(updateInfo: AppUpdateInfo?) {
    val info = updateInfo ?: return
    val context = requireContext()

    val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    // En Android 13+ el permiso de notificaciones es necesario para mostrarla;
    // si no lo tenemos, ocultamos la notificación para que el downloadmanager
    // encole la descarga igualmente.
    val notificationVisibility = if (notificationPermissionGranted) {
        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
    } else {
        DownloadManager.Request.VISIBILITY_HIDDEN
    }

    val request = DownloadManager.Request(info.apkUrl.toUri())
        .setTitle("WalacTV ${info.latestVersionName}")
        .setDescription("Descargando actualizacion")
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(notificationVisibility)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "WalacTV-${info.latestVersionName}.apk")

    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = try {
        dm.enqueue(request)
    } catch (e: Exception) {
        Log.e(ComposeMainFragment.TAG, "Fallo al encolar la descarga de la actualizacion: ${e.message}", e)
        -1L
    }

    if (downloadId == -1L) {
        isUpdateDownloading = false
        updateErrorMessage = "No se pudo iniciar la descarga. Revisa el permiso de instalacion."
        updateStatusMessage = "Error al iniciar la descarga"
        return
    }

    pendingUpdateDownloadId = downloadId
    isUpdateDownloading = true
    updateStatusMessage = "Descargando actualizacion ${info.latestVersionName}"
    Toast.makeText(context, updateStatusMessage, Toast.LENGTH_SHORT).show()

    startUpdateDownloadPolling(downloadId)
}

internal fun ComposeMainFragment.startUpdateDownloadPolling(downloadId: Long) {
    updateDownloadPollJob?.cancel()
    updateDownloadPollJob = scope.launch {
        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        var lastProgress = -1L
        while (isActive && isUpdateDownloading && pendingUpdateDownloadId == downloadId) {
            delay(2_000L)
            dm.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    // Download record vanished — fall back to broadcast
                    return@launch
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesSoFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        // Cancel polling — the broadcast should handle it, but if it didn't fire we handle it here
                        isUpdateDownloading = false
                        pendingUpdateDownloadId = null
                        val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        val info = mandatoryUpdate ?: availableUpdate
                        if (info != null && localUri != null) {
                            launchApkInstaller(localUri, info.latestVersionName)
                        } else {
                            updateErrorMessage = "La descarga termino pero no se pudo abrir el APK"
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        isUpdateDownloading = false
                        pendingUpdateDownloadId = null
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Log.e(ComposeMainFragment.TAG, "Descarga de actualizacion fallida (poll). Reason: $reason")
                        updateErrorMessage = "No se pudo descargar la actualizacion (error $reason)"
                        updateStatusMessage = "Error al descargar la actualizacion"
                        return@launch
                    }
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                        if (totalBytes > 0 && bytesSoFar != lastProgress) {
                            lastProgress = bytesSoFar
                            val pct = (bytesSoFar * 100 / totalBytes).toInt()
                            updateStatusMessage = "Descargando actualizacion ${mandatoryUpdate?.latestVersionName ?: ""} ($pct%)"
                        }
                    }
                }
            }
        }
    }
}

internal fun ComposeMainFragment.handleCompletedUpdateDownload(downloadId: Long) {
    updateDownloadPollJob?.cancel()
    val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query().setFilterById(downloadId)
    dm.query(query).use { cursor ->
        if (!cursor.moveToFirst()) return
        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                // Dedup: if polling already handled this, skip
                if (!isUpdateDownloading && pendingUpdateDownloadId == null) return
                isUpdateDownloading = false; pendingUpdateDownloadId = null
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val updateInfo = mandatoryUpdate ?: availableUpdate
                if (updateInfo != null && localUri != null) {
                    launchApkInstaller(localUri, updateInfo.latestVersionName)
                } else {
                    updateErrorMessage = "La descarga termino pero no se pudo abrir el APK"
                }
            }
            DownloadManager.STATUS_FAILED -> {
                isUpdateDownloading = false; pendingUpdateDownloadId = null
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                Log.e(ComposeMainFragment.TAG, "Descarga de actualizacion fallida. Reason: $reason")
                updateErrorMessage = "No se pudo descargar la actualizacion"
                updateStatusMessage = "Error al descargar la actualizacion"
            }
        }
    }
}

internal fun ComposeMainFragment.launchApkInstaller(localUri: String, versionName: String) {
    val context = requireContext()

    // Try to get a readable URI: prefer FileProvider, fall back to direct file URI
    val contentUri: Uri = try {
        val apkFile = resolveApkFile(localUri, versionName)
        if (apkFile != null && apkFile.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } else {
            // Fall back to the DownloadManager URI directly
            val uri = Uri.parse(localUri)
            if (uri.scheme == "content") {
                // DownloadManager on some devices returns content:// URIs — use directly
                uri
            } else if (uri.scheme == "file") {
                // Try FileProvider with the path from the file:// URI
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } else {
                    Log.e(ComposeMainFragment.TAG, "APK no encontrado en: ${file.absolutePath}")
                    updateErrorMessage = "No se encontro el APK descargado"
                    return
                }
            } else {
                // Give the URI directly to the installer
                uri
            }
        }
    } catch (e: Exception) {
        Log.e(ComposeMainFragment.TAG, "No se pudo crear el content URI para el APK: ${e.message}", e)
        updateErrorMessage = "No se pudo abrir el APK: ${e.message}"
        return
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e(ComposeMainFragment.TAG, "No se pudo abrir el instalador: ${e.message}", e)
        updateErrorMessage = "No se pudo abrir el instalador: ${e.message}"
    }
}

private fun ComposeMainFragment.resolveApkFile(localUri: String, versionName: String): File? {
    // Primary: reconstruct from known destination path
    if (versionName.isNotBlank()) {
        val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null) {
            val file = File(dir, "WalacTV-${versionName}.apk")
            if (file.exists()) return file
        }
    }
    // Secondary: parse from the file:// URI
    val uri = Uri.parse(localUri)
    if (uri.scheme == "file") {
        val file = File(uri.path ?: "")
        if (file.exists()) return file
    }
    return null
}
