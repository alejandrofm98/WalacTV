package com.example.walactv

import com.example.walactv.ui.compose.COUNTRY_FILTER_DIALOG_TITLE
import com.example.walactv.ui.compose.COUNTRY_FILTER_LABEL
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterComponentsTest {

    @Test
    fun `uses Pais label for country filter`() {
        assertEquals("Pais", COUNTRY_FILTER_LABEL)
    }

    @Test
    fun `uses Selecciona pais title for country dialog`() {
        assertEquals("Selecciona pais", COUNTRY_FILTER_DIALOG_TITLE)
    }
}
