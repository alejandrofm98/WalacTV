package com.example.walactv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCatalogModelsTest {

    @Test
    fun `channel page strips duplicated channel number from displayed title`() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "id": "101",
                  "type": "channel",
                  "title": "101 La 1 HD",
                  "display_name": "La 1 HD",
                  "channel_number": 101,
                  "group": "ES | TDT"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = parseRemoteCatalogPage(payload).items.single()

        assertEquals("La 1 HD", item.title)
    }

    @Test
    fun `channel page prefers cleaned display name over numbered normalized title`() {
        val payload = JSONObject(
            """
            {
              "items": [
                {
                  "id": "101",
                  "type": "channel",
                  "normalized_title": "101 La 1 HD",
                  "display_name": "La 1 HD",
                  "channel_number": 101,
                  "group": "ES | TDT"
                }
              ]
            }
            """.trimIndent(),
        )

        val item = parseRemoteCatalogPage(payload).items.single()

        assertEquals("La 1 HD", item.title)
    }
}
