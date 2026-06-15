package com.example.walactv.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamOptionTest {

    @Test
    fun toUnifiedOptions_createsUnifiedOptionFromStreamOption() {
        val streamOptions = listOf(
            StreamOption(
                label = "Directo",
                url = "https://example.com/stream",
                language = "ES",
                quality = "HD",
            ),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
        assertEquals("Español", unified[0].language)
        assertEquals("HD", unified[0].quality)
        assertEquals("https://example.com/stream", unified[0].url)
    }

    @Test
    fun toUnifiedOptions_skipsOptionsWithoutLanguage() {
        val streamOptions = listOf(
            StreamOption(label = "Directo", url = "https://example.com/stream"),
            StreamOption(label = "HD", url = "https://example.com/stream2", language = "ES"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
        assertEquals("Español", unified[0].language)
    }

    @Test
    fun toUnifiedOptions_detectsQualityFromLabel() {
        val streamOptions = listOf(
            StreamOption(label = "FHD", url = "https://example.com/stream", language = "EN"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
        assertEquals("FHD", unified[0].quality)
    }

    @Test
    fun toUnifiedOptions_defaultsToHDWhenNoKnownQuality() {
        val streamOptions = listOf(
            StreamOption(label = "Directo", url = "https://example.com/stream", language = "ES"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
        assertEquals("HD", unified[0].quality)
    }

    @Test
    fun toUnifiedOptions_normalizesLanguageCode() {
        val streamOptions = listOf(
            StreamOption(label = "Stream", url = "https://example.com/stream", language = "ESPANOL"),
            StreamOption(label = "Stream2", url = "https://example.com/stream2", language = "ENGLISH"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(2, unified.size)
        val languages = unified.map { it.language }.toSet()
        assertTrue(languages.contains("Español"))
        assertTrue(languages.contains("Inglés"))
    }

    @Test
    fun toUnifiedOptions_deduplicatesByLanguageAndQuality() {
        val streamOptions = listOf(
            StreamOption(label = "HD", url = "https://example.com/stream1", language = "ES", quality = "HD"),
            StreamOption(label = "HD", url = "https://example.com/stream2", language = "ES", quality = "HD"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(1, unified.size)
    }

    @Test
    fun toUnifiedOptions_sortsByQualityDescending() {
        val streamOptions = listOf(
            StreamOption(label = "SD", url = "https://example.com/sd", language = "ES", quality = "SD"),
            StreamOption(label = "UHD", url = "https://example.com/uhd", language = "ES", quality = "UHD"),
            StreamOption(label = "HD", url = "https://example.com/hd", language = "ES", quality = "HD"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(3, unified.size)
        assertEquals("UHD", unified[0].quality)
        assertEquals("HD", unified[1].quality)
        assertEquals("SD", unified[2].quality)
    }

    @Test
    fun toUnifiedOptions_sortsByLanguageWhenQualityEqual() {
        val streamOptions = listOf(
            StreamOption(label = "HD", url = "https://example.com/es", language = "ES", quality = "HD"),
            StreamOption(label = "HD", url = "https://example.com/en", language = "EN", quality = "HD"),
        )

        val unified = streamOptions.toUnifiedOptions()

        assertEquals(2, unified.size)
        assertEquals("Español", unified[0].language)
        assertEquals("Inglés", unified[1].language)
    }

    @Test
    fun unifiedStreamOption_displayLabel_combinesLanguageAndQuality() {
        val option = UnifiedStreamOption(
            language = "Español",
            quality = "HD",
            url = "https://example.com/stream",
        )

        assertEquals("Español HD", option.displayLabel)
    }

    @Test
    fun toUnifiedOptions_emptyList_returnsEmpty() {
        val unified = emptyList<StreamOption>().toUnifiedOptions()
        assertTrue(unified.isEmpty())
    }

    @Test
    fun toUnifiedOptions_allKnownQualityTokens_detected() {
        val knownTokens = listOf("UHD", "4K", "FHD", "HD", "SD", "HEVC", "H265", "HQ", "LQ")

        knownTokens.forEach { token ->
            val streamOptions = listOf(
                StreamOption(label = token, url = "https://example.com/stream", language = "ES"),
            )
            val unified = streamOptions.toUnifiedOptions()
            assertEquals(1, unified.size, "Token $token should be detected")
            assertEquals(token, unified[0].quality, "Quality should be $token")
        }
    }
}
