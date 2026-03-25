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
    // Find the opening quote of the value, then walk forward respecting
    // backslash-escaped characters so that \n, \", etc. are handled correctly.
    val keyPattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"")
    val match = keyPattern.find(json) ?: return ""
    val start = match.range.last + 1 // position right after the opening "
    val sb = StringBuilder()
    var i = start
    while (i < json.length) {
        val c = json[i]
        if (c == '\\' && i + 1 < json.length) {
            val next = json[i + 1]
            when (next) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                'r' -> sb.append('\r')
                '\\' -> sb.append('\\')
                '"' -> sb.append('"')
                else -> {
                    sb.append('\\')
                    sb.append(next)
                }
            }
            i += 2
        } else if (c == '"') {
            break
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString().trim()
}

private fun extractApkUrl(json: String): String {
    // Busca browser_download_url de APKs directamente en el JSON completo
    // sin intentar parsear la estructura anidada con regex
    val urlRegex = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"")
    return urlRegex.find(json)?.groupValues?.getOrNull(1).orEmpty()
}
