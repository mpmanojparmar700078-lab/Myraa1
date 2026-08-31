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

    init {
        // Start foreground service if enabled
        if (memoryRepository.isForegroundServiceEnabled()) {
            MyraAssistantForegroundService.startService(application)
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
}
