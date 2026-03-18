package com.example.walactv

data class InstalledAppVersion(
    val versionName: String,
    val versionCode: Int,
)

data class AppUpdateInfo(
    val latestVersionName: String,
    val latestVersionCode: Int,
    val minSupportedCode: Int,
    val apkUrl: String,
    val changelog: String,
    val fetchedAtMillis: Long,
)

enum class AppUpdateAvailability {
    UP_TO_DATE,
    OPTIONAL,
    REQUIRED,
}

const val DEFAULT_APP_UPDATE_URL = "https://raw.githubusercontent.com/alejandrofm98/WalacTV/main/version.json"

fun parseAppUpdateInfo(json: String, fetchedAtMillis: Long = System.currentTimeMillis()): AppUpdateInfo? {
    val info = AppUpdateInfo(
        latestVersionName = extractJsonString(json, "latestVersionName"),
        latestVersionCode = extractJsonInt(json, "latestVersionCode"),
        minSupportedCode = extractJsonInt(json, "minSupportedCode"),
        apkUrl = extractJsonString(json, "apkUrl"),
        changelog = extractJsonString(json, "changelog"),
        fetchedAtMillis = fetchedAtMillis,
    )
    return if (info.isValid()) info else null
}

fun evaluateAppUpdate(installed: InstalledAppVersion, remote: AppUpdateInfo): AppUpdateAvailability {
    return when {
        installed.versionCode < remote.minSupportedCode -> AppUpdateAvailability.REQUIRED
        installed.versionCode < remote.latestVersionCode -> AppUpdateAvailability.OPTIONAL
        else -> AppUpdateAvailability.UP_TO_DATE
    }
}

fun isValidUpdateUrl(url: String): Boolean {
    val normalized = url.trim()
    return normalized.startsWith("https://") || normalized.startsWith("http://")
}

fun resolveAppUpdateUrl(configuredUrl: String?): String {
    val normalized = configuredUrl?.trim().orEmpty()
    return if (normalized.isBlank()) DEFAULT_APP_UPDATE_URL else normalized
}

private fun AppUpdateInfo.isValid(): Boolean {
    if (latestVersionName.isBlank()) return false
    if (latestVersionCode <= 0 || minSupportedCode <= 0) return false
    if (minSupportedCode > latestVersionCode) return false
    if (!isValidUpdateUrl(apkUrl)) return false
    return true
}

private fun extractJsonString(json: String, key: String): String {
    val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
    return regex.find(json)?.groupValues?.getOrNull(1)?.trim().orEmpty()
}

private fun extractJsonInt(json: String, key: String): Int {
    val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
    return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}
