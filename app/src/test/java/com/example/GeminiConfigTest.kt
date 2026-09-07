package com.example

import com.example.services.GeminiConfig
import com.example.services.GeminiErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiConfigTest {

    @Test
    fun testGeminiConfig_ModelConfig() {
        assertEquals("gemini-3.6-flash", GeminiConfig.DEFAULT_MODEL)
        assertTrue(GeminiConfig.FALLBACK_MODELS.contains("gemini-3.6-flash"))
        assertFalse(GeminiConfig.FALLBACK_MODELS.contains("gemini-2.0-flash"))
        assertFalse(GeminiConfig.FALLBACK_MODELS.contains("gemini-1.5-flash"))

        val url = GeminiConfig.getGenerateContentUrl(apiKey = "TEST_KEY_123")
        assertTrue(url.contains("models/gemini-3.6-flash:generateContent?key=TEST_KEY_123"))
        assertTrue(url.startsWith("https://generativelanguage.googleapis.com/v1beta/models"))
    }

    @Test
    fun testGeminiErrorTypes_Coverage() {
        // Verify all error types exist and are distinct
        val types = GeminiErrorType.values().map { it.name }
        assertTrue(types.contains("MODEL_NOT_FOUND"))
        assertTrue(types.contains("INVALID_API_KEY"))
        assertTrue(types.contains("RATE_LIMITED"))
        assertTrue(types.contains("NETWORK_ERROR"))
        assertTrue(types.contains("SERVER_ERROR"))
        assertTrue(types.contains("UNKNOWN_ERROR"))
    }
}
