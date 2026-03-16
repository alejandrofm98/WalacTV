package com.example.walactv

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogModelsTest {

    private fun createItem(group: String): CatalogItem {
        return CatalogItem(
            stableId = "1",
            title = "Title",
            subtitle = "Subtitle",
            description = "Desc",
            imageUrl = "",
            kind = ContentKind.MOVIE,
            group = group,
            badgeText = "",
            streamOptions = emptyList()
        )
    }

    @Test
    fun `extracts idioma and subgrupo with pipe separator`() {
        val item = createItem("ES | Accion")
        assertEquals("ES", item.idioma)
        assertEquals("Accion", item.subgrupo)
    }

    @Test
    fun `extracts idioma and subgrupo with dash separator`() {
        val item = createItem("LATAM - Comedia")
        assertEquals("LATAM", item.idioma)
        assertEquals("Comedia", item.subgrupo)
    }

    @Test
    fun `extracts idioma and subgrupo with no separator`() {
        val item = createItem("Documentales")
        assertEquals("Todos", item.idioma)
        assertEquals("Documentales", item.subgrupo)
    }

    @Test
    fun `handles extra whitespace around separator`() {
        val item = createItem("EN| Drama ")
        assertEquals("EN", item.idioma)
        assertEquals("Drama", item.subgrupo)
    }

    private fun createSeriesItem(title: String): CatalogItem {
        return CatalogItem(
            stableId = "series:1",
            title = title,
            subtitle = "Subtitle",
            description = "Desc",
            imageUrl = "",
            kind = ContentKind.SERIES,
            group = "Series",
            badgeText = "",
            streamOptions = emptyList()
        )
    }

    @Test
    fun `extracts series info from English format`() {
        val metadata = parseSeriesMetadata("Breaking Bad S01 E05 - Ozymandias", ContentKind.SERIES)
        assertEquals("Breaking Bad", metadata.seriesName)
        assertEquals(1, metadata.seasonNumber)
        assertEquals(5, metadata.episodeNumber)
    }

    @Test
    fun `extracts series info from Spanish format`() {
        val metadata = parseSeriesMetadata("Los Soprano T06 E12", ContentKind.SERIES)
        assertEquals("Los Soprano", metadata.seriesName)
        assertEquals(6, metadata.seasonNumber)
        assertEquals(12, metadata.episodeNumber)
    }

    @Test
    fun `returns nulls for non-matching series`() {
        val metadata = parseSeriesMetadata("Just A Movie Name", ContentKind.SERIES)
        assertEquals("Just A Movie Name", metadata.seriesName)
        assertEquals(null, metadata.seasonNumber)
        assertEquals(null, metadata.episodeNumber)
    }
}
