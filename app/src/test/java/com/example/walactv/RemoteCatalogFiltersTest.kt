package com.example.walactv

import com.example.walactv.data.model.CatalogFilters
import com.example.walactv.data.model.ContentKind
import com.example.walactv.data.remote.api.dto.FilterOptionDto
import com.example.walactv.data.remote.parser.buildCatalogQuery
import com.example.walactv.data.remote.parser.buildGroupsQuery
import com.example.walactv.data.remote.parser.buildRemoteCatalogFilters
import com.example.walactv.data.remote.parser.parseRemoteFilterOptions
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
                    FilterOptionDto(value = "ES", label = "ES"),
                    FilterOptionDto(value = "MX", label = "MX"),
                ),
                groups = listOf(
                    FilterOptionDto(value = "Accion", label = "Accion"),
                    FilterOptionDto(value = "Drama", label = "Drama"),
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
                FilterOptionDto(value = "ES", label = "Espana"),
                FilterOptionDto(value = "US", label = "Estados Unidos"),
                FilterOptionDto(value = "MX", label = "Mexico"),
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
                FilterOptionDto(value = "ES", label = "ES"),
                FilterOptionDto(value = "MX", label = "MX"),
                FilterOptionDto(value = "US", label = "US"),
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
                FilterOptionDto(value = "Accion", label = "Accion"),
                FilterOptionDto(value = "Drama", label = "Drama"),
            ),
            parseRemoteFilterOptions(payload, "groups"),
        )
    }
}
