package com.example.voice

/**
 * Represents the current operational state of the Speech-to-Text engine.
 */
enum class SpeechState {
    IDLE,
    INITIALIZING,
    LISTENING,
    PROCESSING,
    ERROR
}

/**
 * Supported language configurations for Speech-to-Text and voice interactions.
 */
enum class SpeechLanguage(val code: String, val displayName: String, val bcp47: String) {
    BILINGUAL("hi_en", "Bilingual (Hindi + English)", "hi-IN"),
    HINDI("hi", "Hindi (हिन्दी)", "hi-IN"),
    ENGLISH("en", "English (India)", "en-IN");

    companion object {
        fun fromCode(code: String?): SpeechLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) }
                ?: BILINGUAL
        }
    }
}

/**
 * Result snapshot from speech recognition.
 */
data class SpeechRecognitionResult(
    val transcript: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val language: String = "auto"
)
