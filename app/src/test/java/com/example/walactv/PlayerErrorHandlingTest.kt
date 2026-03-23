package com.example.walactv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerErrorHandlingTest {

    @Test
    fun `detects fatal codec incompatibility messages`() {
        assertTrue(isFatalPlaybackErrorForDevice("Decoder failed: NO_EXCEEDS_CAPABILITIES"))
        assertTrue(isFatalPlaybackErrorForDevice("video codec dolby-vision unsupported"))
        assertFalse(isFatalPlaybackErrorForDevice("network timeout"))
    }
}
