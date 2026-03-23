package com.example.walactv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCatalogMappingTest {

    @Test
    fun `maps home payload into sections and searchable items`() {
        val payload = JSONObject(
            """
            {
              "featured_channels": [
                {
                  "id": "101",
                  "type": "channel",
                  "title": "ES - Noticias 24",
                  "normalized_title": "Noticias 24",
                  "subtitle": "ES - Informacion",
                  "normalized_group": "Informacion",
                  "description": "ES - Informacion",
                  "image_url": "https://img/channel.png",
                  "group": "ES - Informacion",
                  "badge_text": "INFO",
                  "channel_number": 24,
                  "stream_url": "https://stream/channel.m3u8"
                }
              ],
              "featured_movies": [
                {
                  "id": "201",
                  "type": "movie",
                  "title": "Movie One",
                  "subtitle": "Accion",
                  "description": "Accion",
                  "image_url": "https://img/movie.png",
                  "group": "Accion",
                  "badge_text": "CINE",
                  "stream_url": "https://stream/movie.mp4"
                }
              ],
              "featured_series": []
            }
            """.trimIndent(),
        )

        val catalog = parseRemoteHomeCatalog(payload)

        assertEquals(2, catalog.sections.size)
        assertEquals("Canales", catalog.sections[0].title)
        assertEquals("Noticias 24", catalog.sections[0].items.first().title)
        assertEquals("Informacion", catalog.sections[0].items.first().group)
        assertEquals(ContentKind.CHANNEL, catalog.sections[0].items.first().kind)
        assertEquals("movie:201", catalog.searchableItems.last().stableId)
    }

    @Test
    fun `maps paginated search payload into catalog items`() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "id": "301",
                  "type": "series",
                  "title": "ES - Serie Uno S01 E01",
                  "normalized_title": "Serie Uno S01 E01",
                  "subtitle": "ES - Drama",
                  "normalized_group": "Drama",
                  "description": "ES - Drama",
                  "image_url": "https://img/series.png",
                  "group": "ES - Drama",
                  "badge_text": "SERIE",
                  "series_name": "Serie Uno",
                  "season_number": 1,
                  "episode_number": 1,
                  "stream_url": "https://stream/series.mp4"
                }
              ],
              "total": 1,
              "page": 1,
              "page_size": 20,
              "pages": 1,
              "has_next": false,
              "has_prev": false
            }
            """.trimIndent(),
        )

        val page = parseRemoteCatalogPage(payload)

        assertEquals(1, page.items.size)
        assertEquals("Serie Uno S01 E01", page.items.first().title)
        assertEquals("Drama", page.items.first().group)
        assertEquals("Serie Uno", page.items.first().seriesName)
        assertFalse(page.hasNext)
    }

    @Test
    fun `maps channel name and logo url fallbacks from paginated payload`() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "id": "401",
                  "type": "channel",
                  "name": "1838 US| 24/7 ALL THE FAMILY",
                  "logo_url": "https://img/dazn.png",
                  "group": "US| 24/7 MOVIES & SERIES",
                  "stream_url": "https://stream/channel.m3u8"
                }
              ],
              "total": 1,
              "page": 1,
              "page_size": 20,
              "pages": 1,
              "has_next": false,
              "has_prev": false
            }
            """.trimIndent(),
        )

        val page = parseRemoteCatalogPage(payload)

        assertEquals("24/7 ALL THE FAMILY", page.items.first().title)
        assertEquals("https://img/dazn.png", page.items.first().imageUrl)
        assertEquals("24/7 MOVIES & SERIES", page.items.first().group)
        assertEquals("US", page.items.first().languageLabel)
        assertEquals(1838, page.items.first().channelNumber)
    }

    @Test
    fun `uses expected page kind when backend omits item type`() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "id": "501",
                  "title": "Movie Fallback",
                  "group": "Drama",
                  "stream_url": "https://stream/movie.mp4"
                }
              ],
              "total": 1,
              "page": 1,
              "page_size": 50,
              "pages": 1
            }
            """.trimIndent(),
        )

        val page = parseRemoteCatalogPage(payload, expectedKind = ContentKind.MOVIE)

        assertEquals(ContentKind.MOVIE, page.items.single().kind)
        assertEquals("movie:501", page.items.single().stableId)
    }

    @Test
    fun `groups featured series as series when backend omits item type`() {
        val payload = JSONObject(
            """
            {
              "featured_series": [
                {
                  "id": "601",
                  "title": "Serie Uno S01 E01",
                  "group": "Drama",
                  "series_name": "Serie Uno",
                  "stream_url": "https://stream/series.mp4"
                }
              ]
            }
            """.trimIndent(),
        )

        val catalog = parseRemoteHomeCatalog(payload)

        assertTrue(catalog.sections.isNotEmpty())
        assertEquals(ContentKind.SERIES, catalog.sections.single().items.single().kind)
        assertEquals("series_group:Serie Uno", catalog.sections.single().items.single().stableId)
    }

    @Test
    fun `infers has next when backend omits flag but page is lower than total pages`() {
        val payload = JSONObject(
            """
            {
              "items": [],
              "total": 100,
              "page": 1,
              "page_size": 50,
              "pages": 2
            }
            """.trimIndent(),
        )

        val page = parseRemoteCatalogPage(payload)

        assertTrue(page.hasNext)
    }

    @Test
    fun `infers has prev when backend omits flag but current page is greater than one`() {
        val payload = JSONObject(
            """
            {
              "items": [],
              "total": 100,
              "page": 2,
              "page_size": 50,
              "pages": 2
            }
            """.trimIndent(),
        )

        val page = parseRemoteCatalogPage(payload)

        assertTrue(page.hasPrev)
    }

    @Test
    fun `replaces stream url placeholders with user credentials`() {
        assertEquals(
            "http://iptv.walerike.com/live/admin/secret/176861",
            resolveStreamTemplate(
                "http://iptv.walerike.com/live/{{USERNAME}}/{{PASSWORD}}/176861",
                "admin",
                "secret",
            ),
        )
        assertEquals(
            "http://iptv.walerike.com/movie/admin/secret/2021299.mkv",
            resolveStreamTemplate(
                "http://iptv.walerike.com/movie/{{USERNAME}}/{{PASSWORD}}/2021299.mkv",
                "admin",
                "secret",
            ),
        )
    }
}
