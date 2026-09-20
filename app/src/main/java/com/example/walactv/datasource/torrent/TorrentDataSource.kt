package com.example.walactv.datasource.torrent

import androidx.media3.common.util.UnstableApi

/**
 * Clasificador de URLs torrent (magnet:) para las ramas de reproduccion.
 * Los bytes los sirve TorrServer por HTTP; esta clase ya no lee del motor.
 */
@UnstableApi
object TorrentDataSourceFactory {
    fun isTorrentUrl(url: String): Boolean = url.startsWith("magnet:")
}
