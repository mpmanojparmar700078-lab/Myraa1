package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.platform.android.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechToTextManager(
    private val context: Context,
    private val onResult: (String, Boolean) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(language: SpeechLanguage = SpeechLanguage.BILINGUAL) {
        if (!PermissionManager.isMicrophoneGranted(context)) {
            _errorMessage.value = "माइक्रोफोन अनुमति (RECORD_AUDIO) आवश्यक है। कृपया अनुमति दें।"
            _speechState.value = SpeechState.ERROR
            return
        }

        mainHandler.post {
            try {
                stopListeningInternal()

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _errorMessage.value = "डिवाइस में स्पीच रिकग्निशन उपलब्ध नहीं है। सिस्टम वॉयस डायलॉग का उपयोग करें।"
                    _speechState.value = SpeechState.ERROR
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _speechState.value = SpeechState.LISTENING
                            _errorMessage.value = null
                        }

                        override fun onBeginningOfSpeech() {
                            _speechState.value = SpeechState.LISTENING
                        }

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _speechState.value = SpeechState.PROCESSING
                        }

                        override fun onError(error: Int) {
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "ऑडियो रिकॉर्डिंग त्रुटि"
                                SpeechRecognizer.ERROR_CLIENT -> "क्लाइंट त्रुटि"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक्रोफोन अनुमति नहीं है"
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "नेटवर्क कनेक्शन त्रुटि"
                                SpeechRecognizer.ERROR_NO_MATCH -> "कोई आवाज़ नहीं पहचानी गई"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "स्पीच रिकग्नाइज़र व्यस्त है"
                                SpeechRecognizer.ERROR_SERVER -> "सर्वर त्रुटि"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "समय समाप्त हो गया"
                                else -> "त्रुटि कोड: $error"
                            }
                            _errorMessage.value = msg
                            _speechState.value = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                SpeechState.IDLE
                            } else {
                                SpeechState.ERROR
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            _speechState.value = SpeechState.IDLE
                            _errorMessage.value = null
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                onResult(matches[0], true)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                onResult(matches[0], false)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = createSpeechRecognizerIntent(language)
                speechRecognizer?.startListening(intent)
                _speechState.value = SpeechState.INITIALIZING
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "वॉयस इनपुट प्रारंभ करने में असमर्थ"
                _speechState.value = SpeechState.ERROR
            }
        }
    }

    fun createSpeechRecognizerIntent(language: SpeechLanguage = SpeechLanguage.BILINGUAL): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.bcp47)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.bcp47)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Myra से बात करें (Speak to Myra)...")
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // ignore
        } finally {
            speechRecognizer = null
            _speechState.value = SpeechState.IDLE
        }
    }

    fun clearError() {
        _errorMessage.value = null
        if (_speechState.value == SpeechState.ERROR) {
            _speechState.value = SpeechState.IDLE
        }
    }
}
