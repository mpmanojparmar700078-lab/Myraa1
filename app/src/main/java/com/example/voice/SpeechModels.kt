package com.example.voice

enum class SpeechLanguage(val code: String, val displayName: String, val bcp47: String) {
    BILINGUAL("hi_en", "Bilingual (Hindi + English)", "hi-IN"),
    HINDI("hi", "Hindi (हिन्दी)", "hi-IN"),
    ENGLISH("en", "English (India)", "en-IN");

    companion object {
        fun fromCode(code: String?): SpeechLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) }
                ?: BILINGUAL
        }
    }
}

enum class SpeechState {
    IDLE,
    INITIALIZING,
    LISTENING,
    PROCESSING,
    ERROR
}

enum class TtsState {
    IDLE,
    INITIALIZING,
    READY,
    SPEAKING,
    ERROR
}

data class SpeechRecognitionResult(
    val transcript: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val language: String = "auto"
)
