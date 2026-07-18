package com.example.walactv.ui.fragment

import androidx.core.net.toUri

/**
 * Formats a duration in milliseconds as `H:MM:SS` (or `M:SS` if under one hour).
 * Returns `"0:00"` for non-positive values.
 */
internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Derives a Referer header value from a stream URL. For known StreamWish/Filemoon
 * CDNs returns the canonical `https://streamwish.to/`. Otherwise returns the
 * scheme+host of the URL. Returns `""` if the URL cannot be parsed.
 */
internal fun extractReferer(url: String): String {
    return try {
        val uri = url.toUri()
        val host = uri.host ?: return ""

        val isStreamWishCdn = host.contains("streamwish") || host.contains("filemoon") ||
                host.contains("hglamioz") || host.contains("wishembed") || host.contains("swdyu")

        if (isStreamWishCdn) {
            "https://streamwish.to/"
        } else {
            "${uri.scheme}://${uri.host}/"
        }
    } catch (e: Exception) {
        ""
    }
}
