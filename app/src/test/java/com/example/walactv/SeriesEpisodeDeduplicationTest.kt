package com.example.walactv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesEpisodeDeduplicationTest {

    @Test
    fun `counts unique series episodes across languages`() {
        val episodes = listOf(
            seriesEpisode("landman-es-e1", "Landman (2024)", 1, 1, "ES"),
            seriesEpisode("landman-en-e1", "Landman (2024)", 1, 1, "EN"),
            seriesEpisode("landman-es-e2", "Landman (2024)", 1, 2, "ES"),
            seriesEpisode("landman-en-e2", "Landman (2024)", 1, 2, "EN"),
        )

        assertEquals(2, episodes.uniqueSeriesEpisodes().size)
    }

    @Test
    fun `prefers preferred language when deduplicating display episodes`() {
        val episodes = listOf(
            seriesEpisode("landman-es-e1", "Landman (2024)", 1, 1, "ES"),
            seriesEpisode("landman-en-e1", "Landman (2024)", 1, 1, "EN"),
            seriesEpisode("landman-en-e2", "Landman (2024)", 1, 2, "EN"),
            seriesEpisode("landman-es-e2", "Landman (2024)", 1, 2, "ES"),
        )

        val displayEpisodes = episodes.uniqueSeriesEpisodes(preferredLanguage = "EN")

        assertEquals(listOf("EN", "EN"), displayEpisodes.map { it.idioma })
        assertEquals(listOf(1, 2), displayEpisodes.map { it.episodeNumber })
    }

    @Test
    fun `falls back to first available language when preferred is missing`() {
        val episodes = listOf(
            seriesEpisode("landman-es-e1", "Landman (2024)", 1, 1, "ES"),
            seriesEpisode("landman-en-e2", "Landman (2024)", 1, 2, "EN"),
        )

        val displayEpisodes = episodes.uniqueSeriesEpisodes(preferredLanguage = "LATAM")

        assertEquals(2, displayEpisodes.size)
        assertTrue(displayEpisodes.any { it.idioma == "ES" && it.episodeNumber == 1 })
        assertTrue(displayEpisodes.any { it.idioma == "EN" && it.episodeNumber == 2 })
    }

    @Test
    fun `finds equivalent episode in target language`() {
        val current = seriesEpisode("landman-es-e1", "Landman (2024)", 1, 1, "ES")
        val episodes = listOf(
            current,
            seriesEpisode("landman-en-e1", "Landman (2024)", 1, 1, "EN"),
            seriesEpisode("landman-es-e2", "Landman (2024)", 1, 2, "ES"),
        )

        val equivalent = episodes.findEquivalentSeriesEpisode(current, "EN")

        assertEquals("landman-en-e1", equivalent?.stableId)
    }

    @Test
    fun `returns null when target language episode does not exist`() {
        val current = seriesEpisode("landman-es-e1", "Landman (2024)", 1, 1, "ES")
        val episodes = listOf(
            current,
            seriesEpisode("landman-es-e2", "Landman (2024)", 1, 2, "ES"),
        )

        val equivalent = episodes.findEquivalentSeriesEpisode(current, "EN")

        assertEquals(null, equivalent)
    }

    private fun seriesEpisode(
        stableId: String,
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int,
        language: String,
    ): CatalogItem {
        return CatalogItem(
            stableId = stableId,
            title = "$seriesName S${seasonNumber.toString().padStart(2, '0')} E${episodeNumber.toString().padStart(2, '0')}",
            subtitle = "",
            description = "",
            imageUrl = "",
            kind = ContentKind.SERIES,
            group = "Test",
            badgeText = "",
            languageLabel = language,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            streamOptions = listOf(StreamOption(label = language, url = "https://example.com/$stableId.mp4")),
        )
    }
}
