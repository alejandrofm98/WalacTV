package com.example.walactv

import com.example.walactv.data.model.PlaybackSourceMode
import com.example.walactv.data.model.StreamOption
import com.example.walactv.data.model.forPlaybackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceModeTest {

    private val iptv = StreamOption(label = "IPTV", url = "https://example.com/movie.m3u8")
    private val torrent = StreamOption(
        label = "Torrent",
        url = "magnet:?xt=urn:btih:abc",
        infoHash = "abc",
    )

    @Test
    fun automaticModeKeepsBothSources() {
        assertEquals(listOf(iptv, torrent), listOf(iptv, torrent).forPlaybackMode(PlaybackSourceMode.AUTO))
    }

    @Test
    fun iptvModeExcludesTorrents() {
        val result = listOf(iptv, torrent).forPlaybackMode(PlaybackSourceMode.IPTV_ONLY)

        assertEquals(listOf(iptv), result)
    }

    @Test
    fun torrentModeExcludesDirectStreams() {
        val result = listOf(iptv, torrent).forPlaybackMode(PlaybackSourceMode.TORRENT_ONLY)

        assertEquals(listOf(torrent), result)
        assertTrue(PlaybackSourceMode.fromStorage("unknown") == PlaybackSourceMode.AUTO)
    }
}
