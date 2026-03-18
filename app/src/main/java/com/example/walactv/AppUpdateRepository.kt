package com.example.walactv

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun installedVersion(): InstalledAppVersion {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }
        return InstalledAppVersion(
            versionName = packageInfo.versionName ?: BuildConfig.VERSION_NAME,
            versionCode = versionCode,
        )
    }

    suspend fun fetchRemoteUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val updateUrl = resolveAppUpdateUrl(BuildConfig.APP_UPDATE_URL)
        if (!isValidUpdateUrl(updateUrl)) return@withContext null

        val connection = (URL(updateUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }

        runCatching {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        }.getOrNull()?.let { body ->
            parseAppUpdateInfo(body)
        }.also {
            connection.disconnect()
        }
    }

    fun loadCachedUpdate(): AppUpdateInfo? {
        val latestVersionName = prefs.getString(KEY_LATEST_VERSION_NAME, null) ?: return null
        val latestVersionCode = prefs.getInt(KEY_LATEST_VERSION_CODE, 0)
        val minSupportedCode = prefs.getInt(KEY_MIN_SUPPORTED_CODE, 0)
        val apkUrl = prefs.getString(KEY_APK_URL, null).orEmpty()
        val changelog = prefs.getString(KEY_CHANGELOG, null).orEmpty()
        val fetchedAtMillis = prefs.getLong(KEY_FETCHED_AT, 0L)

        return AppUpdateInfo(
            latestVersionName = latestVersionName,
            latestVersionCode = latestVersionCode,
            minSupportedCode = minSupportedCode,
            apkUrl = apkUrl,
            changelog = changelog,
            fetchedAtMillis = fetchedAtMillis,
        ).takeIf { isValidUpdateUrl(it.apkUrl) && it.latestVersionCode > 0 && it.minSupportedCode > 0 && it.minSupportedCode <= it.latestVersionCode }
    }

    fun cacheUpdate(info: AppUpdateInfo) {
        prefs.edit()
            .putString(KEY_LATEST_VERSION_NAME, info.latestVersionName)
            .putInt(KEY_LATEST_VERSION_CODE, info.latestVersionCode)
            .putInt(KEY_MIN_SUPPORTED_CODE, info.minSupportedCode)
            .putString(KEY_APK_URL, info.apkUrl)
            .putString(KEY_CHANGELOG, info.changelog)
            .putLong(KEY_FETCHED_AT, info.fetchedAtMillis)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_LATEST_VERSION_NAME = "latest_version_name"
        private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        private const val KEY_MIN_SUPPORTED_CODE = "min_supported_code"
        private const val KEY_APK_URL = "apk_url"
        private const val KEY_CHANGELOG = "changelog"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val NETWORK_TIMEOUT_MS = 5_000
    }
}
