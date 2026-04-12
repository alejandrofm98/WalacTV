package com.example.walactv.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.walactv.AppUpdateAvailability
import com.example.walactv.AppUpdateInfo
import com.example.walactv.ComposeMainFragment
import com.example.walactv.InstalledAppVersion
import com.example.walactv.evaluateAppUpdate
import kotlinx.coroutines.launch

internal fun ComposeMainFragment.restoreCachedUpdateState() {
    val installed = installedAppVersion ?: return
    val cachedUpdate = appUpdateRepository.loadCachedUpdate() ?: return
    availableUpdate = cachedUpdate
    when (evaluateAppUpdate(installed, cachedUpdate)) {
        AppUpdateAvailability.REQUIRED  -> { mandatoryUpdate = cachedUpdate; updateStatusMessage = "Actualizacion obligatoria disponible" }
        AppUpdateAvailability.OPTIONAL  -> { mandatoryUpdate = null; updateStatusMessage = "Nueva version ${cachedUpdate.latestVersionName} disponible" }
        AppUpdateAvailability.UP_TO_DATE -> { mandatoryUpdate = null; updateStatusMessage = "Aplicacion actualizada" }
    }
}

internal fun ComposeMainFragment.checkForAppUpdates(showToast: Boolean = false) {
    scope.launch {
        isCheckingUpdates = true
        updateErrorMessage = null

        val remoteUpdate = runCatching { appUpdateRepository.fetchRemoteUpdate() }.getOrNull()
        if (remoteUpdate == null) {
            updateStatusMessage = if (mandatoryUpdate != null) "Actualizacion obligatoria pendiente" else "No se pudo comprobar"
            if (showToast) Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_LONG).show()
            isCheckingUpdates = false
            return@launch
        }

        appUpdateRepository.cacheUpdate(remoteUpdate)
        availableUpdate = remoteUpdate

        when (evaluateAppUpdate(installedAppVersion ?: return@launch, remoteUpdate)) {
            AppUpdateAvailability.REQUIRED  -> { mandatoryUpdate = remoteUpdate; updateStatusMessage = "Actualizacion obligatoria disponible" }
            AppUpdateAvailability.OPTIONAL  -> { mandatoryUpdate = null; updateStatusMessage = "Nueva version ${remoteUpdate.latestVersionName} disponible" }
            AppUpdateAvailability.UP_TO_DATE -> { mandatoryUpdate = null; updateStatusMessage = "Aplicacion actualizada" }
        }
        if (showToast) Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_SHORT).show()
        isCheckingUpdates = false
    }
}

internal fun ComposeMainFragment.startUpdateFlow() {
    val updateInfo = mandatoryUpdate ?: availableUpdate ?: return
    if (!canRequestPackageInstalls()) {
        pendingInstallPermission = true
        updateStatusMessage = "Permite instalar apps desconocidas para continuar"
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${requireContext().packageName}"))
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            runCatching { startActivity(intent) }.onFailure { updateErrorMessage = "No se pudo abrir la configuracion de instalacion" }
        } else {
            updateErrorMessage = "Activa manualmente la instalacion desde origenes desconocidos para WalacTV"
        }
        return
    }
    startUpdateDownload(updateInfo)
}

internal fun ComposeMainFragment.canRequestPackageInstalls(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            requireContext().packageManager.canRequestPackageInstalls()

internal fun ComposeMainFragment.startUpdateDownload(updateInfo: AppUpdateInfo?) {
    val info = updateInfo ?: return
    val request = DownloadManager.Request(Uri.parse(info.apkUrl))
        .setTitle("WalacTV ${info.latestVersionName}")
        .setDescription("Descargando actualizacion")
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(requireContext(), Environment.DIRECTORY_DOWNLOADS, "WalacTV-${info.latestVersionName}.apk")

    val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    pendingUpdateDownloadId = dm.enqueue(request)
    isUpdateDownloading = true
    updateStatusMessage = "Descargando actualizacion ${info.latestVersionName}"
    Toast.makeText(requireContext(), updateStatusMessage, Toast.LENGTH_SHORT).show()
}

internal fun ComposeMainFragment.handleCompletedUpdateDownload(downloadId: Long) {
    val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val query = DownloadManager.Query().setFilterById(downloadId)
    dm.query(query).use { cursor ->
        if (!cursor.moveToFirst()) return
        when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                isUpdateDownloading = false; pendingUpdateDownloadId = null
                val uri = dm.getUriForDownloadedFile(downloadId)
                if (uri != null) launchApkInstaller(uri) else updateErrorMessage = "La descarga termino pero no se pudo abrir el APK"
            }
            DownloadManager.STATUS_FAILED -> {
                isUpdateDownloading = false; pendingUpdateDownloadId = null
                updateErrorMessage = "No se pudo descargar la actualizacion"
                updateStatusMessage = "Error al descargar la actualizacion"
            }
        }
    }
}

internal fun ComposeMainFragment.launchApkInstaller(apkUri: Uri) {
    val contentUri = try {
        val file = java.io.File(apkUri.path ?: return)
        androidx.core.content.FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
    } catch (_: Exception) { apkUri }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(intent) }.onFailure { updateErrorMessage = "No se pudo abrir el instalador" }
}
