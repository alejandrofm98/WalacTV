package com.example.walactv.data.util

import com.example.walactv.BuildConfig

internal fun isTmdbImagePath(path: String): Boolean {
    if (path.isBlank()) return false
    if (path.startsWith("http://image.tmdb.org") || path.startsWith("https://image.tmdb.org")) return true
    return path.trimStart('/').isNotBlank() && !path.trimStart('/').contains("/")
}

internal fun normalizeRemoteImageUrl(url: String): String {
    if (url.isBlank() || url == "null") return ""
    val trimmedUrl = url.trim()
    val normalizedBaseUrl = BuildConfig.IPTV_BASE_URL.trimEnd('/')
    val normalizedUrl = when {
        trimmedUrl.startsWith("//") -> "https:$trimmedUrl"
        trimmedUrl.startsWith("/") -> "$normalizedBaseUrl$trimmedUrl"
        trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
        else -> "$normalizedBaseUrl/$trimmedUrl"
    }
    return normalizedUrl
        .replace("http://${BuildConfig.IPTV_BASE_URL.removePrefix("https://").removePrefix("http://")}", BuildConfig.IPTV_BASE_URL)
        .replace("http://image.tmdb.org", "https://image.tmdb.org")
}
