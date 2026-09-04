package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Enterprise-grade Speech-to-Text Manager for Myra.
 * Provides hands-free voice command capture using Android's native SpeechRecognizer.
 * Exposes real-time streaming transcripts, sound level metering for UI waveforms,
 * and robust error recovery.
 */
class SpeechToTextManager(private val context: Context) {

    companion object {
        private const val TAG = "MyraSpeechToText"
        private const val DEFAULT_SILENCE_TIMEOUT_MS = 2500L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var speechRecognizer: SpeechRecognizer? = null
    private var onCommandCallback: ((String) -> Unit)? = null
    private var autoSubmitOnFinal: Boolean = true

    // State flows for UI observation
    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f) // Normalized 0.0f .. 1.0f
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _handsFreeEnabled = MutableStateFlow(true)
    val handsFreeEnabled: StateFlow<Boolean> = _handsFreeEnabled.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(SpeechLanguage.BILINGUAL)
    val selectedLanguage: StateFlow<SpeechLanguage> = _selectedLanguage.asStateFlow()

    init {
        initializeRecognizer()
    }

    /**
     * Checks if Speech Recognition service is installed and available on this device.
     */
    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun setHandsFreeEnabled(enabled: Boolean) {
        _handsFreeEnabled.value = enabled
    }

    fun setSelectedLanguage(language: SpeechLanguage) {
        _selectedLanguage.value = language
    }

    private fun initializeRecognizer() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                    Log.d(TAG, "SpeechRecognizer successfully initialized")
                } else {
                    Log.w(TAG, "SpeechRecognizer is not available on this device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "SpeechRecognizer onReadyForSpeech")
                _speechState.value = SpeechState.LISTENING
                _lastError.value = null
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "SpeechRecognizer onBeginningOfSpeech")
                _speechState.value = SpeechState.LISTENING
            }

            override fun onRmsChanged(rmsdB: Float) {
                // RMS dB typically ranges from -2.0 to 12.0
                val normalized = ((rmsdB.coerceIn(-2f, 12f) + 2f) / 14f).coerceIn(0f, 1f)
                _soundLevel.value = normalized
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer onEndOfSpeech")
                _speechState.value = SpeechState.PROCESSING
                _soundLevel.value = 0f
            }

            override fun onError(error: Int) {
                val errorMsg = getErrorMessage(error)
                Log.w(TAG, "SpeechRecognizer error: $error ($errorMsg)")
                _soundLevel.value = 0f
                _speechState.value = SpeechState.ERROR
                _lastError.value = errorMsg

                // If recognizer was busy, re-initialize so next tap succeeds immediately
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                    mainHandler.postDelayed({
                        initializeRecognizer()
                    }, 500L)
                }

                // Auto reset state to IDLE after showing error briefly
                scope.launch {
                    delay(2800)
                    if (_speechState.value == SpeechState.ERROR) {
                        _speechState.value = SpeechState.IDLE
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _soundLevel.value = 0f
                _speechState.value = SpeechState.IDLE

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val primaryResult = matches?.firstOrNull()?.trim() ?: ""

                Log.d(TAG, "SpeechRecognizer onResults: $primaryResult")

                if (primaryResult.isNotBlank()) {
                    _partialTranscript.value = primaryResult
                    val callback = onCommandCallback
                    if (autoSubmitOnFinal && callback != null) {
                        callback(primaryResult)
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = partials?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    _partialTranscript.value = text
                    Log.d(TAG, "Partial speech: $text")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Optional system events
            }
        }
    }

    /**
     * Starts listening for user voice commands.
     * @param autoSubmit Whether to automatically dispatch command to Myra when speech ends.
     * @param onCommandCallback Callback triggered with the final transcribed text.
     */
    fun startListening(
        autoSubmit: Boolean = _handsFreeEnabled.value,
        onCommandCallback: (String) -> Unit
    ) {
        this.autoSubmitOnFinal = autoSubmit
        this.onCommandCallback = onCommandCallback

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initializeRecognizer()
                }

                _partialTranscript.value = ""
                _lastError.value = null
                _soundLevel.value = 0f
                _speechState.value = SpeechState.INITIALIZING

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                    // Configure language preferences based on selection
                    when (_selectedLanguage.value) {
                        SpeechLanguage.HINDI -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                        }
                        SpeechLanguage.ENGLISH -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                        }
                        SpeechLanguage.BILINGUAL -> {
                            // Default locale with Hindi/English bilingual hint
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN"))
                        }
                    }

                    // Silence detection timeout
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        DEFAULT_SILENCE_TIMEOUT_MS
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1800L
                    )
                }

                speechRecognizer?.startListening(intent)
                Log.d(TAG, "SpeechRecognizer startListening initiated")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening", e)
                _speechState.value = SpeechState.ERROR
                _lastError.value = "Failed to start speech recognizer: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Stops listening and forces finalization of current speech input.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _speechState.value = SpeechState.PROCESSING
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping listening", e)
            }
        }
    }

    /**
     * Cancels active recognition and resets state without submitting.
     */
    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                _speechState.value = SpeechState.IDLE
                _partialTranscript.value = ""
                _soundLevel.value = 0f
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling listening", e)
            }
        }
    }

    /**
     * Cleans up native recognizer resources.
     */
    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying speech recognizer", e)
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client recognition error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
            SpeechRecognizer.ERROR_NETWORK -> "Network error. Please check internet connection."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout during speech recognition."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking closer to mic."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Resetting..."
            SpeechRecognizer.ERROR_SERVER -> "Speech server error. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic and try again."
            else -> "Speech recognition error ($errorCode)"
        }
    }
}
