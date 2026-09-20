package com.example.walactv.data.model

/** Preferencia de fuente para la reproduccion de peliculas y series. */
enum class PlaybackSourceMode(
    val storageValue: String,
    val displayLabel: String,
) {
    AUTO("auto", "Automatico"),
    IPTV_ONLY("iptv", "IPTV"),
    TORRENT_ONLY("torrent", "Torrent"),
    ;

    companion object {
        fun fromStorage(value: String?): PlaybackSourceMode {
            return entries.firstOrNull { it.storageValue == value } ?: AUTO
        }
    }
}

fun PlaybackSourceMode.allows(stream: StreamOption): Boolean {
    return when (this) {
        PlaybackSourceMode.AUTO -> true
        PlaybackSourceMode.IPTV_ONLY -> !stream.isTorrent && stream.url.isNotBlank()
        PlaybackSourceMode.TORRENT_ONLY -> stream.isTorrent
    }
}

fun List<StreamOption>.forPlaybackMode(mode: PlaybackSourceMode): List<StreamOption> {
    return filter { mode.allows(it) }
}
