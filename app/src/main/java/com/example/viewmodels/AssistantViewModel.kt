package com.example.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MemoryRepository
import com.example.data.MessageEntity
import com.example.data.MyraDatabase
import com.example.models.ActionResult
import com.example.models.AssistantState
import com.example.models.InstalledAppInfo
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.platform.android.AppLauncher
import com.example.services.GeminiService
import com.example.services.LocalCommandParser
import com.example.services.MyraAssistantForegroundService
import com.example.voice.SpeechLanguage
import com.example.voice.SpeechState
import com.example.voice.SpeechToTextManager
import com.example.voice.TextToSpeechManager
import com.example.voice.TtsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MyraDatabase.getDatabase(application)
    val memoryRepository = MemoryRepository(
        messageDao = db.messageDao(),
        preferenceDao = db.preferenceDao(),
        interactionHistoryDao = db.interactionHistoryDao(),
        context = application
    )

    private val appLauncher = AppLauncher(application)
    private val localCommandParser = LocalCommandParser()
    private val geminiService = GeminiService { memoryRepository.getCustomApiKey() }

    private val ttsManager = TextToSpeechManager(application)
    private var sttManager: SpeechToTextManager? = null

    val messages: StateFlow<List<MessageEntity>> = memoryRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _speechState = MutableStateFlow(SpeechState.IDLE)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    val ttsState: StateFlow<TtsState> = ttsManager.ttsState

    private val _speechLanguage = MutableStateFlow(SpeechLanguage.fromCode(memoryRepository.getVoiceLanguage()))
    val speechLanguage: StateFlow<SpeechLanguage> = _speechLanguage.asStateFlow()

    private val _isVoiceOutputEnabled = MutableStateFlow(memoryRepository.isVoiceOutputEnabled())
    val isVoiceOutputEnabled: StateFlow<Boolean> = _isVoiceOutputEnabled.asStateFlow()

    private val _customApiKey = MutableStateFlow(memoryRepository.getCustomApiKey())
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _isForegroundServiceActive = MutableStateFlow(memoryRepository.isForegroundServiceEnabled())
    val isForegroundServiceActive: StateFlow<Boolean> = _isForegroundServiceActive.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    init {
        sttManager = SpeechToTextManager(application) { transcript, isFinal ->
            _inputText.value = transcript
            if (isFinal && transcript.isNotBlank()) {
                processUserQuery(transcript)
            }
        }
        viewModelScope.launch {
            sttManager?.speechState?.collect {
                _speechState.value = it
            }
        }
        loadInstalledApps()
        if (memoryRepository.isForegroundServiceEnabled()) {
            MyraAssistantForegroundService.start(application)
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isNotBlank()) {
            _inputText.value = ""
            processUserQuery(text)
        }
    }

    private fun processUserQuery(query: String) {
        viewModelScope.launch {
            _assistantState.value = AssistantState.PROCESSING
            memoryRepository.saveUserMessage(query)

            // Step 1: Check local intent parser
            val localResult = localCommandParser.parse(query)
            if (localResult.handled && localResult.intent != null) {
                executeIntent(localResult.intent, query)
                return@launch
            }

            // Step 2: Fallback to Gemini AI / intelligent local responses
            val recentContext = messages.value.takeLast(4).joinToString("\n") {
                "${if (it.isUser) "User" else "Myra"}: ${it.text}"
            }
            val aiResponse = geminiService.generateResponse(query, recentContext)

            _assistantState.value = AssistantState.IDLE
            memoryRepository.saveAssistantResponse(
                text = aiResponse,
                intent = ParsedIntent(type = IntentType.GENERAL_CHAT, responseText = aiResponse)
            )

            if (_isVoiceOutputEnabled.value) {
                speak(aiResponse)
            }
        }
    }

    private fun executeIntent(intent: ParsedIntent, rawQuery: String) {
        viewModelScope.launch {
            _assistantState.value = AssistantState.EXECUTING_ACTION
            var actionResult: ActionResult? = null
            var reply = intent.responseText ?: "एक्शन पूरा किया गया।"

            when (intent.type) {
                IntentType.OPEN_APP -> {
                    val targetApp = intent.app?.lowercase() ?: ""
                    actionResult = when {
                        intent.target != null -> appLauncher.launchAppByPackage(intent.target)
                        targetApp.contains("camera") -> appLauncher.launchCamera()
                        targetApp.contains("dialer") || targetApp.contains("phone") -> appLauncher.launchDialer()
                        targetApp.contains("youtube") -> appLauncher.launchAppByPackage(AppLauncher.PKG_YOUTUBE)
                        targetApp.contains("chrome") -> appLauncher.launchAppByPackage(AppLauncher.PKG_CHROME)
                        targetApp.contains("map") -> appLauncher.launchAppByPackage(AppLauncher.PKG_MAPS)
                        else -> {
                            // Find in installed apps
                            val match = _installedApps.value.firstOrNull {
                                it.appName.lowercase().contains(targetApp)
                            }
                            if (match != null) {
                                appLauncher.launchAppByPackage(match.packageName)
                            } else {
                                appLauncher.performWebSearch(rawQuery)
                            }
                        }
                    }
                    reply = actionResult.message
                }

                IntentType.OPEN_SETTINGS -> {
                    actionResult = appLauncher.launchSettings()
                    reply = actionResult.message
                }

                IntentType.WEB_SEARCH -> {
                    val q = intent.query ?: rawQuery
                    actionResult = appLauncher.performWebSearch(q)
                    reply = actionResult.message
                }

                IntentType.YOUTUBE_SEARCH, IntentType.YOUTUBE_SEARCH_AND_PLAY -> {
                    val q = intent.query ?: rawQuery
                    actionResult = appLauncher.searchYouTube(q)
                    reply = actionResult.message
                }

                IntentType.OPEN_URL -> {
                    val url = intent.target ?: rawQuery
                    actionResult = appLauncher.openUrl(url)
                    reply = actionResult.message
                }

                IntentType.CLEAR_CHAT -> {
                    clearChatHistory()
                    _assistantState.value = AssistantState.IDLE
                    return@launch
                }

                else -> {
                    actionResult = ActionResult(success = true, message = reply)
                }
            }

            memoryRepository.saveAssistantResponse(
                text = reply,
                intent = intent,
                result = actionResult
            )

            _assistantState.value = AssistantState.IDLE
            if (_isVoiceOutputEnabled.value) {
                speak(reply)
            }
        }
    }

    fun startListening() {
        ttsManager.stop()
        sttManager?.startListening(_speechLanguage.value)
    }

    fun stopListening() {
        sttManager?.stopListening()
    }

    fun speak(text: String) {
        ttsManager.speak(
            text = text,
            speechRate = memoryRepository.getTtsSpeechRate(),
            pitch = memoryRepository.getTtsPitch()
        )
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            memoryRepository.clearHistory()
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = appLauncher.getInstalledApps()
        }
    }

    fun launchApp(packageName: String) {
        viewModelScope.launch {
            appLauncher.launchAppByPackage(packageName)
        }
    }

    fun setSpeechLanguage(language: SpeechLanguage) {
        _speechLanguage.value = language
        memoryRepository.setVoiceLanguage(language.code)
    }

    fun setVoiceOutputEnabled(enabled: Boolean) {
        _isVoiceOutputEnabled.value = enabled
        memoryRepository.setVoiceOutputEnabled(enabled)
        if (!enabled) {
            ttsManager.stop()
        }
    }

    fun setCustomApiKey(key: String) {
        _customApiKey.value = key
        memoryRepository.setCustomApiKey(key)
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        _isForegroundServiceActive.value = enabled
        memoryRepository.setForegroundServiceEnabled(enabled)
        val app = getApplication<Application>()
        if (enabled) {
            MyraAssistantForegroundService.start(app)
        } else {
            MyraAssistantForegroundService.stop(app)
        }
    }

    fun setTtsSpeechRate(rate: Float) {
        memoryRepository.setTtsSpeechRate(rate)
    }

    fun setTtsPitch(pitch: Float) {
        memoryRepository.setTtsPitch(pitch)
    }

    fun onAppForegrounded() {
        loadInstalledApps()
    }

    fun onAppBackgrounded() {
        stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.release()
        sttManager?.stopListening()
    }
}
