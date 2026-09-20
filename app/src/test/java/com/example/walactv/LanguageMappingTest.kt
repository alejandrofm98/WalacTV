package com.example.walactv

import com.example.walactv.data.util.languageBadgeLabel
import com.example.walactv.data.util.translateBareLanguageCode
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageMappingTest {

    @Test
    fun badgeMapsKnownCodes() {
        assertEquals("Inglés", languageBadgeLabel("EN"))
        assertEquals("Inglés", languageBadgeLabel("eng"))
        assertEquals("Español", languageBadgeLabel("ES"))
        assertEquals("Latino", languageBadgeLabel("LATAM"))
        assertEquals("Castellano", languageBadgeLabel("CAST"))
    }

    @Test
    fun badgeDefaultsToSpanish() {
        assertEquals("Español", languageBadgeLabel(null))
        assertEquals("Español", languageBadgeLabel(""))
    }

    @Test
    fun labelTranslatesBareCodesOnly() {
        assertEquals("Inglés", translateBareLanguageCode("EN"))
        assertEquals("Inglés", translateBareLanguageCode("ENG"))
        assertEquals("Español", translateBareLanguageCode("ES"))
        assertEquals("Latino", translateBareLanguageCode("LATAM"))
    }

    @Test
    fun labelLeavesAnythingElseUntouched() {
        assertEquals("Servidor principal", translateBareLanguageCode("Servidor principal"))
        assertEquals("Ver", translateBareLanguageCode("Ver"))
        assertEquals("Directo", translateBareLanguageCode("Directo"))
        assertEquals("EN 4K", translateBareLanguageCode("EN 4K"))
        assertEquals("", translateBareLanguageCode(null))
    }
}
