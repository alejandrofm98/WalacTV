package com.example.walactv

import com.example.walactv.data.preferences.PreferencesManager
import com.example.walactv.data.preferences.isAudioSelectorEnabled
import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.util.languageDisplayLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesManagerTest {

    @Test
    fun `keeps supported language values`() {
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage("ES"))
        assertEquals("EN", PreferencesManager.normalizePreferredLanguage("EN"))
    }

    @Test
    fun `maps unsupported or empty values to ES`() {
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage("VOSE"))
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage("CAST"))
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage("LATAM"))
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage(null))
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage(""))
    }

    @Test
    fun `maps player language codes to friendly labels`() {
        assertEquals("Español", languageDisplayLabel("ES"))
        assertEquals("Inglés", languageDisplayLabel("EN"))
        assertEquals("Español Latinoamericano", languageDisplayLabel("LATAM"))
    }

    @Test
    fun `audio selector button is disabled for zero or one track`() {
        assertEquals(false, isAudioSelectorEnabled(0))
        assertEquals(false, isAudioSelectorEnabled(1))
        assertEquals(true, isAudioSelectorEnabled(2))
    }

    @Test
    fun `series episodes share playback preference key`() {
        val first = seriesEpisode("episode-1", "series-42")
        val second = seriesEpisode("episode-2", "series-42")

        val firstKey = PreferencesManager.playbackPreferenceKey(
            ContentKind.SERIES,
            first.stableId,
            first,
            "alice",
        )
        val secondKey = PreferencesManager.playbackPreferenceKey(
            ContentKind.SERIES,
            second.stableId,
            second,
            "alice",
        )

        assertEquals(firstKey, secondKey)
    }

    @Test
    fun `movie playback preference key is content and user specific`() {
        val aliceMovie = PreferencesManager.playbackPreferenceKey(
            ContentKind.MOVIE,
            "movie-1",
            username = "alice",
        )
        val bobMovie = PreferencesManager.playbackPreferenceKey(
            ContentKind.MOVIE,
            "movie-1",
            username = "bob",
        )

        assertEquals("alice|MOVIE|movie-1", aliceMovie)
        assertEquals("bob|MOVIE|movie-1", bobMovie)
    }

    private fun seriesEpisode(stableId: String, seriesProviderId: String): CatalogItem {
        return CatalogItem(
            stableId = stableId,
            title = stableId,
            subtitle = "",
            description = "",
            imageUrl = "",
            kind = ContentKind.SERIES,
            group = "",
            badgeText = "",
            seriesProviderId = seriesProviderId,
        )
    }
}
