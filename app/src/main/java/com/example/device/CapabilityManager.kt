package com.example.device

import android.content.Context
import android.content.SharedPreferences
import com.example.services.MyraAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MyraCapabilitiesState(
    val hasMicrophonePermission: Boolean = false,
    val isBackgroundListeningEnabled: Boolean = false,
    val hasScreenCaptureCapability: Boolean = false,
    val isAccessibilityServiceEnabled: Boolean = false,
    val isGameInteractionEnabled: Boolean = false,
    val isAutoMode: Boolean = false,
    val askBeforeActions: Boolean = true,
    val isWorkSchedulerEnabled: Boolean = true
)

/**
 * Centralized Device Capability Coordinator.
 * Merges system runtime permission status with user preference toggles.
 */
class CapabilityManager(private val context: Context) {

    private val permissionManager = PermissionManager(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("myra_capabilities", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_BACKGROUND_LISTENING = "cap_background_listening"
        private const val PREF_GAME_INTERACTION = "cap_game_interaction"
        private const val PREF_AUTO_MODE = "cap_auto_mode"
        private const val PREF_ASK_BEFORE_ACTIONS = "cap_ask_before_actions"
        private const val PREF_WORK_SCHEDULER = "cap_work_scheduler"
    }

    private val _capabilities = MutableStateFlow(loadCurrentCapabilities())
    val capabilities: StateFlow<MyraCapabilitiesState> = _capabilities.asStateFlow()

    fun refresh() {
        _capabilities.value = loadCurrentCapabilities()
    }

    private fun loadCurrentCapabilities(): MyraCapabilitiesState {
        return MyraCapabilitiesState(
            hasMicrophonePermission = permissionManager.hasMicrophonePermission(),
            isBackgroundListeningEnabled = prefs.getBoolean(PREF_BACKGROUND_LISTENING, false),
            hasScreenCaptureCapability = ScreenCaptureManager.hasProjectionConsent(),
            isAccessibilityServiceEnabled = permissionManager.isAccessibilityServiceEnabled(),
            isGameInteractionEnabled = prefs.getBoolean(PREF_GAME_INTERACTION, true),
            isAutoMode = prefs.getBoolean(PREF_AUTO_MODE, true),
            askBeforeActions = prefs.getBoolean(PREF_ASK_BEFORE_ACTIONS, false),
            isWorkSchedulerEnabled = prefs.getBoolean(PREF_WORK_SCHEDULER, true)
        )
    }

    fun setBackgroundListeningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_BACKGROUND_LISTENING, enabled).apply()
        refresh()
    }

    fun setGameInteractionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_GAME_INTERACTION, enabled).apply()
        refresh()
    }

    fun setAutoMode(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_MODE, enabled).apply()
        refresh()
    }

    fun setAskBeforeActions(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_ASK_BEFORE_ACTIONS, enabled).apply()
        refresh()
    }

    fun setWorkSchedulerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_WORK_SCHEDULER, enabled).apply()
        refresh()
    }

    fun canObserveScreen(): Boolean {
        return permissionManager.isAccessibilityServiceEnabled() || ScreenCaptureManager.hasProjectionConsent()
    }

    fun canInteractWithUi(): Boolean {
        return permissionManager.isAccessibilityServiceEnabled() && MyraAccessibilityService.getInstance() != null
    }

    fun canInteractWithGame(): Boolean {
        return canInteractWithUi() && capabilities.value.isGameInteractionEnabled
    }
}
