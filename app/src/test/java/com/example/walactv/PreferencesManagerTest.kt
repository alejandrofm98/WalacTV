package com.example.walactv

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesManagerTest {

    @Test
    fun `keeps supported language values`() {
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage("ES"))
        assertEquals("EN", PreferencesManager.normalizePreferredLanguage("EN"))
        assertEquals("LATAM", PreferencesManager.normalizePreferredLanguage("LATAM"))
    }

    @Test
    fun `maps unsupported or empty values to ES`() {
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage("VOSE"))
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage("CAST"))
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage(null))
        assertEquals("ES", PreferencesManager.normalizePreferredLanguage(""))
    }

    @Test
    fun `maps player language codes to friendly labels`() {
        assertEquals("Español", languageDisplayLabel("ES"))
        assertEquals("Inglés", languageDisplayLabel("EN"))
        assertEquals("Español Latinoamericano", languageDisplayLabel("LATAM"))
    }

    @Test
    fun `audio selector button is disabled for zero or one track`() {
        assertEquals(false, isAudioSelectorEnabled(0))
        assertEquals(false, isAudioSelectorEnabled(1))
        assertEquals(true, isAudioSelectorEnabled(2))
    }
}
