package com.example.walactv

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
}
