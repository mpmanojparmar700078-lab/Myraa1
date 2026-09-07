package com.example.services

/**
 * Central configuration for Gemini API models and endpoints in Myra.
 * All services and components obtain model identifiers and URLs from here.
 */
object GeminiConfig {
    /**
     * Primary supported Gemini Flash model.
     */
    const val DEFAULT_MODEL = "gemini-3.6-flash"

    /**
     * Fallback models in case of transient model unavailability.
     */
    val FALLBACK_MODELS = listOf(DEFAULT_MODEL, "gemini-3.5-flash", "gemini-2.5-flash")

    /**
     * Base endpoint for Google Generative Language API.
     */
    const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    /**
     * Constructs the generateContent endpoint URL for a given model and API key.
     */
    fun getGenerateContentUrl(model: String = DEFAULT_MODEL, apiKey: String): String {
        return "$API_BASE_URL/$model:generateContent?key=$apiKey"
    }
}

/**
 * Explicit error classification for Gemini API responses.
 */
enum class GeminiErrorType {
    MODEL_NOT_FOUND,
    INVALID_API_KEY,
    RATE_LIMITED,
    NETWORK_ERROR,
    SERVER_ERROR,
    UNKNOWN_ERROR
}

data class GeminiErrorDetails(
    val type: GeminiErrorType,
    val httpCode: Int?,
    val rawMessage: String,
    val userFriendlyMessage: String
)
