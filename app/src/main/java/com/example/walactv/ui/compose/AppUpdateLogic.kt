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
import com.example.walactv.AppUpdateAvailability
import com.example.walactv.AppUpdateInfo
import com.example.walactv.ComposeMainFragment
import com.example.walactv.evaluateAppUpdate
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
}

internal fun ComposeMainFragment.handleCompletedUpdateDownload(downloadId: Long) {
    val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query().setFilterById(downloadId)
    dm.query(query).use { cursor ->
        if (!cursor.moveToFirst()) return
        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
            DownloadManager.STATUS_SUCCESSFUL -> {
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

    // Preferimos el archivo de destino conocido para servirlo a traves de FileProvider.
    val apkFile = if (versionName.isNotBlank()) {
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "WalacTV-${versionName}.apk")
    } else {
        localUri.takeIf { it.startsWith("file://") }?.let { File(Uri.parse(it).path ?: "") }
    }

    if (apkFile == null || !apkFile.exists()) {
        Log.e(ComposeMainFragment.TAG, "APK descargado no encontrado en ${apkFile?.absolutePath}")
        updateErrorMessage = "No se encontro el APK descargado"
        return
    }

    val contentUri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    } catch (e: Exception) {
        Log.e(ComposeMainFragment.TAG, "No se pudo crear el content URI para el APK: ${e.message}", e)
        updateErrorMessage = "No se pudo abrir el APK"
        return
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(intent) }.onFailure { e ->
        Log.e(ComposeMainFragment.TAG, "No se pudo abrir el instalador: ${e.message}", e)
        updateErrorMessage = "No se pudo abrir el instalador"
    }
}
