package com.example.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MyraApplication
import com.example.data.InteractionHistoryEntity
import com.example.data.MemoryRepository
import com.example.data.MessageEntity
import com.example.data.UserPreferenceEntity
import com.example.models.AssistantState
import com.example.services.AssistantService
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

    private val db = (application as MyraApplication).database
    val memoryRepository = MemoryRepository(
        messageDao = db.messageDao(),
        preferenceDao = db.preferenceDao(),
        interactionHistoryDao = db.interactionHistoryDao(),
        context = application
    )

    val assistantService = AssistantService(
        context = application,
        memoryRepository = memoryRepository
    )

    val messages: StateFlow<List<MessageEntity>> = memoryRepository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val preferences: StateFlow<List<UserPreferenceEntity>> = memoryRepository.allPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val interactionHistory: StateFlow<List<InteractionHistoryEntity>> = memoryRepository.interactionHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    val assistantState: StateFlow<AssistantState> = assistantService.assistantState
    val currentActionProgress: StateFlow<com.example.models.ActionProgressUpdate?> = assistantService.currentActionProgress
    val isServiceRunning: StateFlow<Boolean> = MyraAssistantForegroundService.isServiceRunning

    val isAccessibilityEnabled: StateFlow<Boolean> = com.example.services.MyraAccessibilityService.isServiceEnabled
    val currentScreenState: StateFlow<com.example.models.ScreenState?> = com.example.services.MyraAccessibilityService.currentScreenState

    private val _isAccessibilityDevMode = MutableStateFlow(false)
    val isAccessibilityDevMode: StateFlow<Boolean> = _isAccessibilityDevMode.asStateFlow()

    private val _customApiKey = MutableStateFlow(memoryRepository.getCustomApiKey())
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _serviceEnabled = MutableStateFlow(memoryRepository.isForegroundServiceEnabled())
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    private val appLauncher = com.example.platform.android.AppLauncher(application)
    private val _installedApps = MutableStateFlow<List<com.example.platform.android.InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<com.example.platform.android.InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    // Public API Integration & Explorer
    val apiRegistry = com.example.apis.registry.ApiRegistry.getInstance(application)
    val apiToolRouter = assistantService.apiToolRouter
    val apis: StateFlow<List<com.example.apis.models.ApiDefinition>> = apiRegistry.apisFlow
    val apiCallHistory: StateFlow<List<com.example.apis.models.ApiCallRecord>> = apiRegistry.callHistoryFlow

    private val _apiTestResult = MutableStateFlow<com.example.apis.models.ApiResponse?>(null)
    val apiTestResult: StateFlow<com.example.apis.models.ApiResponse?> = _apiTestResult.asStateFlow()

    private val _isTestingApi = MutableStateFlow(false)
    val isTestingApi: StateFlow<Boolean> = _isTestingApi.asStateFlow()

    // Device Permissions & Capability Management
    val permissionManager = com.example.device.PermissionManager(application)
    val capabilityManager = com.example.device.CapabilityManager(application)
    val capabilities: StateFlow<com.example.device.MyraCapabilitiesState> = capabilityManager.capabilities

    // Autonomous Task Engine
    val taskEngine = com.example.automation.TaskEngine(
        context = application,
        capabilityManager = capabilityManager
    )
    val activeTask: StateFlow<com.example.automation.AutonomousTask?> = taskEngine.activeTask

    // WorkManager Background Task Scheduler
    val backgroundScheduler: com.example.work.MyraBackgroundScheduler =
        (application as MyraApplication).backgroundScheduler
    val schedulerEnabled: StateFlow<Boolean> = backgroundScheduler.isSchedulerEnabled
    val schedulerIntervalMinutes: StateFlow<Long> = backgroundScheduler.selectedIntervalMinutes
    val schedulerLastRunTimestamp: StateFlow<Long> = backgroundScheduler.lastRunTimestamp
    val schedulerLastRunSummary: StateFlow<String> = backgroundScheduler.lastRunSummary
    val schedulerWorkStatus: StateFlow<String> = backgroundScheduler.workStatus

    // Speech-to-Text & Hands-free Voice Engine
    val speechToTextManager = SpeechToTextManager(application)
    val speechState: StateFlow<SpeechState> = speechToTextManager.speechState
    val partialTranscript: StateFlow<String> = speechToTextManager.partialTranscript
    val speechSoundLevel: StateFlow<Float> = speechToTextManager.soundLevel
    val speechError: StateFlow<String?> = speechToTextManager.lastError
    val isHandsFreeVoiceEnabled: StateFlow<Boolean> = speechToTextManager.handsFreeEnabled
    val selectedSpeechLanguage: StateFlow<SpeechLanguage> = speechToTextManager.selectedLanguage
    val isSpeechRecognitionAvailable: Boolean = speechToTextManager.isRecognitionAvailable()

    // Text-to-Speech (TTS) Voice Output Engine
    val textToSpeechManager = TextToSpeechManager(application)
    val isTtsSpeaking: StateFlow<Boolean> = textToSpeechManager.isSpeaking
    val ttsState: StateFlow<TtsState> = textToSpeechManager.ttsState
    val isVoiceOutputEnabled: StateFlow<Boolean> = textToSpeechManager.voiceOutputEnabled
    private val _ttsSpeechRate = MutableStateFlow(memoryRepository.getTtsSpeechRate())
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()

    init {
        // Start foreground service if enabled
        if (memoryRepository.isForegroundServiceEnabled()) {
            MyraAssistantForegroundService.startService(application)
        }
        speechToTextManager.setHandsFreeEnabled(memoryRepository.isHandsFreeVoiceEnabled())
        speechToTextManager.setSelectedLanguage(SpeechLanguage.fromCode(memoryRepository.getVoiceLanguage()))

        // Initialize voice output preferences and bind assistant responses to TTS speech
        textToSpeechManager.setVoiceOutputEnabled(memoryRepository.isVoiceOutputEnabled())
        textToSpeechManager.setSpeechRate(memoryRepository.getTtsSpeechRate())
        textToSpeechManager.setPitch(memoryRepository.getTtsPitch())

        assistantService.onAssistantResponseCallback = { replyText ->
            if (textToSpeechManager.voiceOutputEnabled.value) {
                textToSpeechManager.speak(replyText)
            }
        }

        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingApps.value = true
            val apps = appLauncher.getInstalledApps()
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun launchAppViaTextCommand(appName: String) {
        sendCommand("$appName खोलो")
    }

    fun sendCommand(input: String) {
        assistantService.processCommand(input)
    }

    fun cancelCurrentRequest() {
        assistantService.cancelCurrentRequest(notifyUser = true)
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            memoryRepository.clearHistory()
        }
    }

    fun saveCustomApiKey(key: String) {
        memoryRepository.setCustomApiKey(key)
        _customApiKey.value = key
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        memoryRepository.setForegroundServiceEnabled(enabled)
        _serviceEnabled.value = enabled
        val app = getApplication<Application>()
        if (enabled) {
            MyraAssistantForegroundService.startService(app)
        } else {
            MyraAssistantForegroundService.stopService(app)
        }
    }

    fun updateLanguagePreference(langCode: String) {
        viewModelScope.launch {
            memoryRepository.setPreference(MemoryRepository.KEY_LANGUAGE, langCode)
        }
    }

    fun openAccessibilitySettings() {
        val app = getApplication<Application>()
        com.example.services.MyraAccessibilityService.openAccessibilitySettings(app)
    }

    fun toggleAccessibilityDevMode(enabled: Boolean) {
        _isAccessibilityDevMode.value = enabled
    }

    fun isAccessibilitySystemEnabled(): Boolean {
        val app = getApplication<Application>()
        return com.example.services.MyraAccessibilityService.isAccessibilityServiceEnabled(app)
    }

    fun onAppForegrounded() {
        assistantService.setForegroundActive()
    }

    fun onAppBackgrounded() {
        assistantService.setBackgroundReady()
    }

    fun toggleApiEnabled(apiId: String, enabled: Boolean) {
        apiRegistry.setApiEnabled(apiId, enabled)
    }

    fun testApi(api: com.example.apis.models.ApiDefinition, customParams: Map<String, String> = emptyMap()) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isTestingApi.value = true
            _apiTestResult.value = null
            try {
                val result = apiToolRouter.routeAndExecute(
                    apiId = api.id,
                    parameters = customParams
                )
                _apiTestResult.value = result
            } catch (e: Exception) {
                _apiTestResult.value = com.example.apis.models.ApiResponse(
                    success = false,
                    statusCode = 0,
                    rawJson = "",
                    formattedSummary = "Test failed: ${e.localizedMessage}",
                    errorMessage = e.localizedMessage,
                    apiId = api.id,
                    apiName = api.name
                )
            } finally {
                _isTestingApi.value = false
            }
        }
    }

    fun clearApiTestResult() {
        _apiTestResult.value = null
    }

    fun reloadApiCatalog() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            apiRegistry.loadCatalogFromAssets()
        }
    }

    // Automation & Capability Handlers
    fun refreshCapabilities() {
        capabilityManager.refresh()
    }

    fun setBackgroundListeningEnabled(enabled: Boolean) {
        capabilityManager.setBackgroundListeningEnabled(enabled)
    }

    fun setGameInteractionEnabled(enabled: Boolean) {
        capabilityManager.setGameInteractionEnabled(enabled)
    }

    fun setAutoMode(enabled: Boolean) {
        capabilityManager.setAutoMode(enabled)
    }

    fun setAskBeforeActions(enabled: Boolean) {
        capabilityManager.setAskBeforeActions(enabled)
    }

    // WorkManager Background Task Scheduler Handlers
    fun setWorkSchedulerEnabled(enabled: Boolean) {
        capabilityManager.setWorkSchedulerEnabled(enabled)
        if (enabled) {
            backgroundScheduler.schedulePeriodicOrchestration()
        } else {
            backgroundScheduler.cancelPeriodicOrchestration()
        }
    }

    fun setWorkSchedulerInterval(intervalMinutes: Long) {
        backgroundScheduler.schedulePeriodicOrchestration(intervalMinutes = intervalMinutes)
    }

    fun runImmediateWorkScheduler() {
        backgroundScheduler.runImmediateOrchestration()
    }

    // Autonomous Task Controls
    fun cancelAutonomousTask() {
        taskEngine.cancelCurrentTask()
    }

    fun startAutonomousTask(goal: String, targetApp: String? = null) {
        taskEngine.startTask(
            goal = goal,
            targetApp = targetApp,
            onStatusUpdate = { status ->
                // Also optionally record progress to assistant state
            }
        )
    }

    // Voice & Speech-to-Text Action Handlers
    fun startVoiceListening(autoSubmit: Boolean = isHandsFreeVoiceEnabled.value) {
        speechToTextManager.startListening(autoSubmit = autoSubmit) { recognizedText ->
            if (recognizedText.isNotBlank()) {
                sendCommand(recognizedText)
            }
        }
    }

    fun stopVoiceListening() {
        speechToTextManager.stopListening()
    }

    fun cancelVoiceListening() {
        speechToTextManager.cancelListening()
    }

    fun setHandsFreeVoiceEnabled(enabled: Boolean) {
        memoryRepository.setHandsFreeVoiceEnabled(enabled)
        speechToTextManager.setHandsFreeEnabled(enabled)
    }

    fun setSpeechLanguage(language: SpeechLanguage) {
        memoryRepository.setVoiceLanguage(language.code)
        speechToTextManager.setSelectedLanguage(language)
    }

    // Voice Output & Text-to-Speech (TTS) Action Handlers
    fun speakText(text: String, force: Boolean = true) {
        textToSpeechManager.speak(text, forceIfDisabled = force)
    }

    fun stopSpeaking() {
        textToSpeechManager.stop()
    }

    fun setVoiceOutputEnabled(enabled: Boolean) {
        memoryRepository.setVoiceOutputEnabled(enabled)
        textToSpeechManager.setVoiceOutputEnabled(enabled)
    }

    fun setTtsSpeechRate(rate: Float) {
        _ttsSpeechRate.value = rate
        memoryRepository.setTtsSpeechRate(rate)
        textToSpeechManager.setSpeechRate(rate)
    }

    fun setTtsPitch(pitch: Float) {
        memoryRepository.setTtsPitch(pitch)
        textToSpeechManager.setPitch(pitch)
    }

    override fun onCleared() {
        super.onCleared()
        speechToTextManager.destroy()
        textToSpeechManager.shutdown()
    }
}
