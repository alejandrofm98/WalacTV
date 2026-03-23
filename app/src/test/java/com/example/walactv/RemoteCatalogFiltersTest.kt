package com.example.walactv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCatalogFiltersTest {

    @Test
    fun `builds movie filters with remote countries and groups`() {
        val countriesPayload = JSONObject(
            """
            {
              "countries": ["ES", "MX"]
            }
            """.trimIndent(),
        )
        val groupsPayload = JSONObject(
            """
            {
              "groups": ["Accion", "Drama"]
            }
            """.trimIndent(),
        )

        assertEquals(
            CatalogFilters(
                countries = listOf(
                    CatalogFilterOption(value = "ES", label = "ES"),
                    CatalogFilterOption(value = "MX", label = "MX"),
                ),
                groups = listOf(
                    CatalogFilterOption(value = "Accion", label = "Accion"),
                    CatalogFilterOption(value = "Drama", label = "Drama"),
                ),
            ),
            buildRemoteCatalogFilters(ContentKind.MOVIE, countriesPayload, groupsPayload),
        )
    }

    @Test
    fun `builds catalog query with countries parameter`() {
        assertEquals(
            "content_type=movies&page=1&page_size=50&country=ES&group=Drama&search=test",
            buildCatalogQuery(
                contentType = "movies",
                page = 1,
                country = "ES",
                group = "Drama",
                search = "test",
            ),
        )
    }

    @Test
    fun `builds groups query with selected country list`() {
        assertEquals(
            "content_type=channels&countries=US%2CMX%2CES",
            buildGroupsQuery(
                contentType = "channels",
                countries = "US,MX,ES",
            ),
        )
    }

    @Test
    fun `parses object filter entries using name as label and code as value`() {
        val payload = JSONObject(
            """
            {
              "countries": [
                {"code": "US", "name": "Estados Unidos"},
                {"code": "MX", "name": "Mexico"},
                {"code": "ES", "name": "Espana"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                CatalogFilterOption(value = "ES", label = "Espana"),
                CatalogFilterOption(value = "US", label = "Estados Unidos"),
                CatalogFilterOption(value = "MX", label = "Mexico"),
            ),
            parseRemoteFilterOptions(payload, "countries"),
        )
    }

    @Test
    fun `parses countries payload into sorted distinct values`() {
        val payload = JSONObject(
            """
            {
              "countries": ["US", "MX", "US", "", "ES"]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                CatalogFilterOption(value = "ES", label = "ES"),
                CatalogFilterOption(value = "MX", label = "MX"),
                CatalogFilterOption(value = "US", label = "US"),
            ),
            parseRemoteFilterOptions(payload, "countries"),
        )
    }

    @Test
    fun `parses groups payload into sorted distinct values`() {
        val payload = JSONObject(
            """
            {
              "groups": ["Drama", "Accion", "Drama", ""]
            }
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                CatalogFilterOption(value = "Accion", label = "Accion"),
                CatalogFilterOption(value = "Drama", label = "Drama"),
            ),
            parseRemoteFilterOptions(payload, "groups"),
        )
    }
}
