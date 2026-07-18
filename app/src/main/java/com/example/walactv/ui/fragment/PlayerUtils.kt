package com.example.walactv.ui.fragment

import android.view.KeyEvent
import android.view.View
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player

/**
 * Matches URLs that go through the iptv-api's channel stream proxy:
 * `http(s)://host/user/pass/<channelId>`. The `?` query string is stripped
 * before matching.
 */
private val CHANNEL_PROXY_REGEX =
    Regex("https?://[^/]+/[^/]+/[^/]+/\\d+$", RegexOption.IGNORE_CASE)

/**
 * Returns true if the given URL points to the iptv-api channel stream proxy
 * (i.e. it carries the username/password/channelId path that the proxy
 * requires). Used to decide whether the player needs to fetch a fresh
 * stream URL or can resume the existing one.
 */
internal fun isChannelProxyUrl(url: String): Boolean {
    val normalized = url.substringBefore('?')
    return CHANNEL_PROXY_REGEX.containsMatchIn(normalized)
}

/**
 * Builds a [MediaItem] from a raw stream URL, picking the right MIME type
 * based on the URL suffix or the iptv-api proxy shape:
 * - `.m3u8` or channel-proxy URL → HLS
 * - `/live/`, `stream-proxy`, or `.ts` → MPEG-TS
 * - `/movie/`, `/series/`, `.mp4`, `.mkv`, `.avi` → default ExoPlayer sniffing
 * - anything else → plain URI
 */
internal fun createMediaItem(url: String): MediaItem {
    return when {
        url.contains(".m3u8", ignoreCase = true) -> {
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        }
        isChannelProxyUrl(url) -> {
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build()
        }
        url.contains("/live/", ignoreCase = true) -> {
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build()
        }
        url.contains("stream-proxy", ignoreCase = true) || url.endsWith(".ts", ignoreCase = true) -> {
            MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build()
        }
        url.contains("/movie/", ignoreCase = true) ||
                url.contains("/series/", ignoreCase = true) ||
                url.endsWith(".mp4", ignoreCase = true) ||
                url.endsWith(".mkv", ignoreCase = true) ||
                url.endsWith(".avi", ignoreCase = true) -> {
            MediaItem.Builder().setUri(url).build()
        }
        else -> MediaItem.fromUri(url)
    }
}

/**
 * Maps a D-pad [KeyEvent] keycode to a single digit (0-9) for the channel
 * zapping buffer, or `null` if the keycode is not a digit.
 */
internal fun mapDigit(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
    else -> null
}

/**
 * Converts a [Player] playback state int into a human-readable name for logs.
 */
internal fun playbackStateName(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN($state)"
}

/**
 * Returns a short, human-readable description of a [View] for debug logs:
 * tries the resource entry name, falls back to `id=...` if the id is not
 * a resource id (anonymous views). Pure function.
 */
internal fun describeView(view: View): String {
    return try {
        val resName = view.resources.getResourceEntryName(view.id)
        "$resName(${view.javaClass.simpleName})"
    } catch (e: Exception) {
        "id=${view.id}(${view.javaClass.simpleName})"
    }
}

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
