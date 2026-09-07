package com.example.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brain.MyraCore
import com.example.data.ExperienceEntity
import com.example.data.LearnedSkillEntity
import com.example.data.MemoryRepository
import com.example.data.MessageEntity
import com.example.data.MyraDatabase
import com.example.models.AssistantState
import com.example.models.DiagnosticLog
import com.example.models.InstalledAppInfo
import com.example.models.IntentType
import com.example.platform.android.AppLauncher
import com.example.services.GeminiService
import com.example.services.LocalCommandParser
import com.example.services.MyraAssistantForegroundService
import com.example.voice.SpeechLanguage
import com.example.voice.SpeechState
import com.example.voice.SpeechToTextManager
import com.example.voice.TextToSpeechManager
import com.example.voice.TtsState
import kotlinx.coroutines.CancellationException
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
        experienceDao = db.experienceDao(),
        learnedSkillDao = db.learnedSkillDao(),
        failedStrategyDao = db.failedStrategyDao(),
        learnedFactDao = db.learnedFactDao(),
        context = application
    )

    private val appLauncher = AppLauncher(application)
    private val localCommandParser = LocalCommandParser()
    private val geminiService = GeminiService { memoryRepository.getCustomApiKey() }

    val myraCore = MyraCore(
        context = application,
        memoryRepository = memoryRepository,
        appLauncher = appLauncher,
        localCommandParser = localCommandParser,
        geminiService = geminiService
    )

    private val ttsManager = TextToSpeechManager(application)
    private var sttManager: SpeechToTextManager? = null

    val messages: StateFlow<List<MessageEntity>> = memoryRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learnedSkills: StateFlow<List<LearnedSkillEntity>> = memoryRepository.learnedSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val experiences: StateFlow<List<ExperienceEntity>> = memoryRepository.experiences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skillCount: StateFlow<Int> = memoryRepository.skillCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val experienceCount: StateFlow<Int> = memoryRepository.experienceCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val diagnosticLogs: StateFlow<List<DiagnosticLog>> = myraCore.diagnosticLogs

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

    private val _isAutoLearningEnabled = MutableStateFlow(memoryRepository.isAutoLearningEnabled())
    val isAutoLearningEnabled: StateFlow<Boolean> = _isAutoLearningEnabled.asStateFlow()

    private val _isGeminiFallbackEnabled = MutableStateFlow(memoryRepository.isGeminiFallbackEnabled())
    val isGeminiFallbackEnabled: StateFlow<Boolean> = _isGeminiFallbackEnabled.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    val sttErrorMessage: StateFlow<String?> = sttManager?.errorMessage ?: MutableStateFlow(null)

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

    fun cancelCurrentTask() {
        _assistantState.value = AssistantState.CANCELLING
        myraCore.cancelActiveRequest()
        stopSpeaking()
        stopListening()
        _assistantState.value = AssistantState.CANCELLED
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _assistantState.value = AssistantState.IDLE
        }
    }

    private fun processUserQuery(query: String) {
        val job = viewModelScope.launch {
            _assistantState.value = AssistantState.PROCESSING
            memoryRepository.saveUserMessage(query)

            try {
                val recentContext = messages.value.takeLast(4).joinToString("\n") {
                    "${if (it.isUser) "User" else "Myra"}: ${it.text}"
                }

                _assistantState.value = AssistantState.EXECUTING_ACTION
                val result = myraCore.executeUserQuery(
                    rawQuery = query,
                    installedApps = _installedApps.value,
                    recentConversationContext = recentContext
                )

                if (result.intent.type == IntentType.CLEAR_CHAT) {
                    clearChatHistory()
                    _assistantState.value = AssistantState.IDLE
                    return@launch
                }

                _assistantState.value = AssistantState.IDLE
                memoryRepository.saveAssistantResponse(
                    text = result.replyText,
                    intent = result.intent,
                    result = result.actionResult,
                    executionSource = result.decisionSource.name,
                    confidence = result.confidence
                )

                if (_isVoiceOutputEnabled.value) {
                    speak(result.replyText)
                }

            } catch (e: CancellationException) {
                _assistantState.value = AssistantState.CANCELLED
            } catch (e: Exception) {
                _assistantState.value = AssistantState.ERROR
                memoryRepository.saveAssistantResponse(
                    text = "त्रुटि: ${e.localizedMessage}",
                    executionSource = "ERROR"
                )
            }
        }
        myraCore.setActiveJob(job)
    }

    fun startListening() {
        ttsManager.stop()
        sttManager?.startListening(_speechLanguage.value)
    }

    fun stopListening() {
        sttManager?.stopListening()
    }

    fun onSpeechResult(transcript: String) {
        val trimmed = transcript.trim()
        if (trimmed.isNotBlank()) {
            _inputText.value = trimmed
            processUserQuery(trimmed)
        }
    }

    fun getSpeechIntent(): android.content.Intent {
        return sttManager?.createSpeechRecognizerIntent(_speechLanguage.value)
            ?: android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    }

    fun clearSttError() {
        sttManager?.clearError()
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

    fun setAutoLearningEnabled(enabled: Boolean) {
        _isAutoLearningEnabled.value = enabled
        memoryRepository.setAutoLearningEnabled(enabled)
    }

    fun setGeminiFallbackEnabled(enabled: Boolean) {
        _isGeminiFallbackEnabled.value = enabled
        memoryRepository.setGeminiFallbackEnabled(enabled)
    }

    fun deleteSkill(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteSkill(id)
        }
    }

    fun deleteExperience(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteExperience(id)
        }
    }

    fun resetLearnedBrain() {
        viewModelScope.launch {
            memoryRepository.resetAllLearnedKnowledge()
            myraCore.logDiagnostic("MYRA_CORE", "All learned knowledge, skills, and experiences reset by user.")
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
        myraCore.cancelActiveRequest()
    }
}
