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

const val GITHUB_RELEASES_API = "https://api.github.com/repos/alejandrofm98/WalacTV/releases/latest"

fun parseAppUpdateInfo(json: String, fetchedAtMillis: Long = System.currentTimeMillis()): AppUpdateInfo? {
    val tagName = extractJsonString(json, "tag_name")
    val versionName = tagName.removePrefix("v").trim()
    val versionCode = versionName.split(".").let { parts ->
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        major * 100 + minor
    }
    val apkUrl = extractApkUrl(json)
    val changelog = extractJsonString(json, "body")

    val info = AppUpdateInfo(
        latestVersionName = versionName,
        latestVersionCode = versionCode,
        minSupportedCode = 1,
        apkUrl = apkUrl,
        changelog = changelog,
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

private fun AppUpdateInfo.isValid(): Boolean {
    if (latestVersionName.isBlank()) return false
    if (latestVersionCode <= 0) return false
    if (!isValidUpdateUrl(apkUrl)) return false
    return true
}

private fun extractJsonString(json: String, key: String): String {
    val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
    return regex.find(json)?.groupValues?.getOrNull(1)?.trim().orEmpty()
}

private fun extractApkUrl(json: String): String {
    val assetBlock = Regex("\"assets\"\\s*:\\s*\\[(.+?)\\]", RegexOption.DOT_MATCHES_ALL)
        .find(json)?.groupValues?.getOrNull(1) ?: return ""
    val assets = Regex("\\{[^}]+\\}").findAll(assetBlock).map { it.value }
    for (asset in assets) {
        val contentType = extractJsonString(asset, "content_type")
        if (contentType == "application/vnd.android.package-archive") {
            return extractJsonString(asset, "browser_download_url")
        }
    }
    return ""
}
