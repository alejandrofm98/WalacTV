package com.example.walactv

import com.example.walactv.data.model.CatalogItem
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.model.StreamOption
import com.example.walactv.data.remote.api.dto.WatchProgressDto
import com.example.walactv.data.util.buildSeriesEpisodeProgressMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesEpisodeProgressMatchingTest {

    @Test
    fun `matches watched episode by any stream provider id`() {
        val episode = episode(
            providerId = "100",
            seriesName = "Catalog title",
            season = 1,
            number = 2,
            streamProviderIds = listOf("100", "200"),
        )
        val watched = WatchProgressDto(
            contentId = "series:200",
            contentType = "series",
            seriesName = "Different historical title",
            isWatched = true,
        )

        val result = buildSeriesEpisodeProgressMap(
            episodes = listOf(episode),
            progressItems = listOf(watched),
            seriesIds = setOf("catalog-id"),
            seriesNames = setOf("Catalog title"),
        )

        assertEquals(watched, result[1 to 2])
    }

    @Test
    fun `falls back to normalized series name when episode id changed`() {
        val episode = episode("new-id", "La Casa", 2, 3)
        val watched = WatchProgressDto(
            contentId = "old-id",
            contentType = "series",
            seriesName = "ES - La Casa",
            seasonNumber = 2,
            episodeNumber = 3,
            isWatched = true,
        )

        val result = buildSeriesEpisodeProgressMap(
            episodes = listOf(episode),
            progressItems = listOf(watched),
            seriesIds = emptySet(),
            seriesNames = setOf("La Casa"),
        )

        assertEquals(watched, result[2 to 3])
    }

    @Test
    fun `does not match another series with same episode numbers`() {
        val episode = episode("current-id", "Current series", 1, 1)
        val otherSeries = WatchProgressDto(
            contentId = "other-id",
            contentType = "series",
            seriesName = "Other series",
            seasonNumber = 1,
            episodeNumber = 1,
            isWatched = true,
        )

        val result = buildSeriesEpisodeProgressMap(
            episodes = listOf(episode),
            progressItems = listOf(otherSeries),
            seriesIds = setOf("catalog-id"),
            seriesNames = setOf("Current series"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `does not treat a title prefix as a language code`() {
        val episode = episode("current-id", "Origins", 1, 1)
        val otherSeries = WatchProgressDto(
            contentId = "other-id",
            contentType = "series",
            seriesName = "Dark - Origins",
            seasonNumber = 1,
            episodeNumber = 1,
            isWatched = true,
        )

        val result = buildSeriesEpisodeProgressMap(
            episodes = listOf(episode),
            progressItems = listOf(otherSeries),
            seriesIds = emptySet(),
            seriesNames = setOf("Origins"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `keeps watched state over in-progress duplicate`() {
        val episode = episode("episode-id", "Series", 1, 1)
        val watched = WatchProgressDto(
            contentId = "episode-id",
            contentType = "series",
            isWatched = true,
            lastWatchedAt = "2026-08-01T10:00:00Z",
        )
        val newerProgress = WatchProgressDto(
            contentId = "episode-id",
            contentType = "series",
            isWatched = false,
            lastWatchedAt = "2026-08-02T10:00:00Z",
        )

        val result = buildSeriesEpisodeProgressMap(
            episodes = listOf(episode),
            progressItems = listOf(watched, newerProgress),
            seriesIds = emptySet(),
            seriesNames = setOf("Series"),
        )

        assertEquals(true, result[1 to 1]?.isWatched)
    }

    private fun episode(
        providerId: String,
        seriesName: String,
        season: Int,
        number: Int,
        streamProviderIds: List<String> = listOf(providerId),
    ) = CatalogItem(
        stableId = "series:$providerId",
        catalogId = providerId,
        providerId = providerId,
        title = seriesName,
        subtitle = "",
        description = "",
        imageUrl = "",
        kind = ContentKind.SERIES,
        group = "",
        badgeText = "",
        seriesName = seriesName,
        seriesKey = "catalog-id",
        seasonNumber = season,
        episodeNumber = number,
        streamOptions = streamProviderIds.map { id ->
            StreamOption(label = id, url = "https://example.com/$id", providerId = id)
        },
    )
}
