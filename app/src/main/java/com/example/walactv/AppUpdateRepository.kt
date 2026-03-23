package com.example.walactv

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AppUpdateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

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
        // Verificar conectividad primero
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }

        if (!isConnected) {
            Log.w(TAG, "Sin conexión a internet detectada por el sistema")
            return@withContext null
        }

        val request = Request.Builder()
            .url(GITHUB_RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "WalacTV")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Respuesta GitHub: ${response.code}")
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API respondio con codigo ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                parseAppUpdateInfo(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== ERROR DETALLADO ===")
            Log.e(TAG, "Tipo: ${e.javaClass.name}")
            Log.e(TAG, "Mensaje: ${e.message}")
            Log.e(TAG, "Causa: ${e.cause?.javaClass?.name}: ${e.cause?.message}")
            null
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
        private const val TAG = "AppUpdateRepo"
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_LATEST_VERSION_NAME = "latest_version_name"
        private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        private const val KEY_MIN_SUPPORTED_CODE = "min_supported_code"
        private const val KEY_APK_URL = "apk_url"
        private const val KEY_CHANGELOG = "changelog"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val NETWORK_TIMEOUT_S = 15L
    }
}
