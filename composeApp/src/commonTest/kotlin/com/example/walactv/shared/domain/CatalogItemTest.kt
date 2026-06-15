package com.example.walactv.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogItemTest {

    private fun createTestItem(
        stableId: String = "test_123",
        kind: ContentKind = ContentKind.MOVIE,
        group: String = "ES | Accion",
        streamOptions: List<StreamOption> = emptyList(),
    ) = CatalogItem(
        stableId = stableId,
        title = "Test Title",
        subtitle = "Test Subtitle",
        description = "Test Description",
        imageUrl = "https://example.com/image.jpg",
        kind = kind,
        group = group,
        badgeText = "NEW",
        streamOptions = streamOptions,
    )

    @Test
    fun stableId_isUsedAsProvided() {
        val item = createTestItem(stableId = "custom_id_456")
        assertEquals("custom_id_456", item.stableId)
    }

    @Test
    fun streamOptions_emptyByDefault() {
        val item = createTestItem()
        assertTrue(item.streamOptions.isEmpty())
    }

    @Test
    fun streamOptions_returnsProvidedList() {
        val options = listOf(
            StreamOption(label = "HD", url = "https://example.com/stream1"),
            StreamOption(label = "SD", url = "https://example.com/stream2"),
        )
        val item = createTestItem(streamOptions = options)
        assertEquals(2, item.streamOptions.size)
        assertEquals("HD", item.streamOptions[0].label)
    }

    @Test
    fun contentKind_enumHasExpectedValues() {
        val values = ContentKind.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(ContentKind.EVENT))
        assertTrue(values.contains(ContentKind.CHANNEL))
        assertTrue(values.contains(ContentKind.MOVIE))
        assertTrue(values.contains(ContentKind.SERIES))
    }

    @Test
    fun isVodContent_returnsTrueForMovieAndSeries() {
        val movie = createTestItem(kind = ContentKind.MOVIE)
        val series = createTestItem(kind = ContentKind.SERIES)
        val channel = createTestItem(kind = ContentKind.CHANNEL)
        val event = createTestItem(kind = ContentKind.EVENT)

        assertTrue(movie.isVodContent())
        assertTrue(series.isVodContent())
        assertFalse(channel.isVodContent())
        assertFalse(event.isVodContent())
    }

    @Test
    fun idioma_extractsLanguageFromGroup() {
        val item = createTestItem(group = "ES | Accion")
        assertEquals("ES", item.idioma)
    }

    @Test
    fun idioma_returnsDefaultWhenNoSeparator() {
        val item = createTestItem(group = "Accion")
        assertEquals("Todos", item.idioma)
    }

    @Test
    fun idioma_usesLanguageLabelWhenPresent() {
        val item = createTestItem(group = "ES | Accion").copy(languageLabel = "EN")
        assertEquals("EN", item.idioma)
    }

    @Test
    fun subgrupo_extractsSubgroupFromGroup() {
        val item = createTestItem(group = "ES | Accion")
        assertEquals("Accion", item.subgrupo)
    }

    @Test
    fun subgrupo_returnsFullGroupWhenNoSeparator() {
        val item = createTestItem(group = "Accion")
        assertEquals("Accion", item.subgrupo)
    }

    @Test
    fun subgrupo_usesNormalizedGroupWhenPresent() {
        val item = createTestItem(group = "ES | Accion").copy(normalizedGroup = "Drama")
        assertEquals("Drama", item.subgrupo)
    }

    @Test
    fun searchableText_containsAllSearchableFields() {
        val item = createTestItem().copy(channelNumber = 5)
        val text = item.searchableText()
        assertTrue(text.contains("Test Title"))
        assertTrue(text.contains("Test Subtitle"))
        assertTrue(text.contains("Test Description"))
        assertTrue(text.contains("ES | Accion"))
        assertTrue(text.contains("MOVIE"))
        assertTrue(text.contains("5"))
    }

    @Test
    fun preferredVodPosterUrl_prefersTmdbPosterUrl() {
        val item = createTestItem(kind = ContentKind.MOVIE).copy(
            tmdbPosterUrl = "https://tmdb.org/poster.jpg",
        )
        assertEquals("https://tmdb.org/poster.jpg", item.preferredVodPosterUrl())
    }

    @Test
    fun preferredVodPosterUrl_fallsBackToImageUrl() {
        val item = createTestItem(kind = ContentKind.MOVIE)
        assertEquals("https://example.com/image.jpg", item.preferredVodPosterUrl())
    }

    @Test
    fun preferredCardImageUrl_usesPosterForVodContent() {
        val item = createTestItem(kind = ContentKind.MOVIE).copy(
            tmdbPosterUrl = "https://tmdb.org/poster.jpg",
        )
        assertEquals("https://tmdb.org/poster.jpg", item.preferredCardImageUrl())
    }

    @Test
    fun preferredCardImageUrl_usesImageUrlForChannel() {
        val item = createTestItem(kind = ContentKind.CHANNEL)
        assertEquals("https://example.com/image.jpg", item.preferredCardImageUrl())
    }
}
