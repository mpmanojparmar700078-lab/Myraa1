package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Production-ready Text-to-Speech (TTS) manager providing natural voice output
 * for Myra AI Assistant responses in Hindi and English.
 */
class TextToSpeechManager(
    private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechManager"
        val LOCALE_HINDI = Locale("hi", "IN")
        val LOCALE_ENGLISH = Locale("en", "IN")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _ttsState = MutableStateFlow(TtsState.INITIALIZING)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _voiceOutputEnabled = MutableStateFlow(true)
    val voiceOutputEnabled: StateFlow<Boolean> = _voiceOutputEnabled.asStateFlow()

    private val _currentUtteranceId = MutableStateFlow<String?>(null)
    val currentUtteranceId: StateFlow<String?> = _currentUtteranceId.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private var activeCompletionCallback: (() -> Unit)? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            _ttsState.value = TtsState.INITIALIZING
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error instantiating TextToSpeech", e)
            _ttsState.value = TtsState.ERROR
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.let { engine ->
                // Check Hindi support
                val hindiAvailable = engine.isLanguageAvailable(LOCALE_HINDI)
                Log.d(TAG, "TTS initialized successfully. Hindi available: $hindiAvailable")

                // Configure default speech rate & pitch
                engine.setSpeechRate(_speechRate.value)
                engine.setPitch(_pitch.value)

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        scope.launch {
                            _isSpeaking.value = true
                            _ttsState.value = TtsState.SPEAKING
                            _currentUtteranceId.value = utteranceId
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        scope.launch {
                            _isSpeaking.value = false
                            _ttsState.value = TtsState.READY
                            _currentUtteranceId.value = null
                            activeCompletionCallback?.invoke()
                            activeCompletionCallback = null
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        scope.launch {
                            _isSpeaking.value = false
                            _ttsState.value = TtsState.READY
                            _currentUtteranceId.value = null
                            activeCompletionCallback = null
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(TAG, "TTS utterance error: $errorCode for utteranceId: $utteranceId")
                        scope.launch {
                            _isSpeaking.value = false
                            _ttsState.value = TtsState.READY
                            _currentUtteranceId.value = null
                            activeCompletionCallback = null
                        }
                    }
                })
            }
            _ttsState.value = TtsState.READY
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            _ttsState.value = TtsState.ERROR
            isInitialized = false
        }
    }

    /**
     * Speaks the specified text aloud.
     *
     * @param text The message string to speak
     * @param queueMode TextToSpeech.QUEUE_FLUSH (replaces current) or QUEUE_ADD
     * @param forceIfDisabled If true, speaks even if voiceOutputEnabled is false (e.g. user tapped Speaker icon)
     * @param onComplete Optional callback when speech playback concludes
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        forceIfDisabled: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        if (!forceIfDisabled && !_voiceOutputEnabled.value) {
            Log.d(TAG, "Voice output is disabled in settings. Skipping TTS.")
            return
        }

        val cleaned = cleanTextForSpeech(text)
        if (cleaned.isBlank()) {
            return
        }

        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not ready yet. Re-initializing engine.")
            initializeTts()
            return
        }

        activeCompletionCallback = onComplete

        try {
            val engine = tts ?: return

            // Bilingual smart locale detection
            val isHindi = containsHindi(cleaned)
            val selectedLocale = if (isHindi) {
                if (engine.isLanguageAvailable(LOCALE_HINDI) >= TextToSpeech.LANG_AVAILABLE) {
                    LOCALE_HINDI
                } else {
                    Locale.getDefault()
                }
            } else {
                if (engine.isLanguageAvailable(LOCALE_ENGLISH) >= TextToSpeech.LANG_AVAILABLE) {
                    LOCALE_ENGLISH
                } else {
                    Locale.US
                }
            }

            engine.language = selectedLocale
            engine.setSpeechRate(_speechRate.value)
            engine.setPitch(_pitch.value)

            val utteranceId = "myra_utterance_${UUID.randomUUID()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val result = engine.speak(cleaned, queueMode, params, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                _isSpeaking.value = true
                _ttsState.value = TtsState.SPEAKING
            } else {
                Log.w(TAG, "speak call returned code: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during TTS speak", e)
            _isSpeaking.value = false
            _ttsState.value = TtsState.ERROR
        }
    }

    /**
     * Immediately stops any active audio speech.
     */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        } finally {
            _isSpeaking.value = false
            _ttsState.value = if (isInitialized) TtsState.READY else TtsState.IDLE
            activeCompletionCallback = null
        }
    }

    fun setVoiceOutputEnabled(enabled: Boolean) {
        _voiceOutputEnabled.value = enabled
        if (!enabled) {
            stop()
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = clamped
        tts?.setSpeechRate(clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 1.8f)
        _pitch.value = clamped
        tts?.setPitch(clamped)
    }

    /**
     * Sanitizes raw markdown text, technical formatting, URLs, and asterisks
     * into clean, pleasant spoken dialogue.
     */
    fun cleanTextForSpeech(input: String): String {
        var text = input

        // Remove markdown bold/italic formatting
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        text = text.replace(Regex("\\*(.*?)\\*"), "$1")
        text = text.replace(Regex("__(.*?)__"), "$1")
        text = text.replace(Regex("_(.*?)_"), "$1")

        // Remove markdown headers and list bullets
        text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")
        text = text.replace(Regex("(?m)^\\s*[-*•]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")

        // Remove inline code and code blocks
        text = text.replace(Regex("```[\\s\\S]*?```"), "Code snippet omitted.")
        text = text.replace(Regex("`([^`]+)`"), "$1")

        // Simplify URLs
        text = text.replace(Regex("https?://\\S+"), "link")

        // Replace common technical JSON/formatting clutter
        text = text.replace("{", "").replace("}", "")
        text = text.replace("[", "").replace("]", "")
        text = text.replace(Regex("[|><~^]"), " ")

        // Clean up excessive whitespace and newlines
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }

    /**
     * Checks if text contains Devanagari Hindi Unicode characters (\u0900..\u097F).
     */
    private fun containsHindi(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        } finally {
            tts = null
            isInitialized = false
            _isSpeaking.value = false
            _ttsState.value = TtsState.IDLE
        }
    }
}
