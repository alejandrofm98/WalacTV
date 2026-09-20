package com.example.walactv

import com.example.walactv.ui.compose.SideRailDestination
import com.example.walactv.ui.compose.buildDefaultSideRailEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideRailConfigTest {

    @Test
    fun `puts Buscar first in side rail`() {
        val items = buildDefaultSideRailEntries()

        assertTrue(items.isNotEmpty())
        assertEquals(SideRailDestination.SEARCH, items.first().destination)
        assertEquals("Buscar", items.first().label)
    }

    @Test
    fun `hides provider only destinations without IPTV`() {
        val items = buildDefaultSideRailEntries(includeIptv = false)

        assertEquals(
            listOf(SideRailDestination.SEARCH, SideRailDestination.HOME, SideRailDestination.DISCOVER),
            items.map { it.destination },
        )
    }
}
