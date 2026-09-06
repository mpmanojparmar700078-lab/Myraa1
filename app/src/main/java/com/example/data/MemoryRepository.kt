package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.models.ActionResult
import com.example.models.ParsedIntent
import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val messageDao: MessageDao,
    private val preferenceDao: PreferenceDao,
    private val interactionHistoryDao: InteractionHistoryDao,
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("myra_settings", Context.MODE_PRIVATE)

    val messages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val allPreferences: Flow<List<UserPreferenceEntity>> = preferenceDao.getAllPreferences()
    val interactionHistory: Flow<List<InteractionHistoryEntity>> = interactionHistoryDao.getAllHistory()

    companion object {
        const val KEY_LANGUAGE = "preferred_language"
        const val PREF_CUSTOM_API_KEY = "custom_gemini_api_key"
        const val PREF_SERVICE_ENABLED = "foreground_service_enabled"
        const val PREF_HANDS_FREE_ENABLED = "hands_free_voice_enabled"
        const val PREF_VOICE_LANGUAGE = "voice_speech_language"
        const val PREF_VOICE_OUTPUT_ENABLED = "voice_output_enabled"
        const val PREF_TTS_SPEECH_RATE = "tts_speech_rate"
        const val PREF_TTS_PITCH = "tts_pitch"
    }

    suspend fun saveUserMessage(text: String, language: String? = null): Long {
        val entity = MessageEntity(
            text = text,
            isUser = true,
            language = language
        )
        return messageDao.insertMessage(entity)
    }

    suspend fun saveAssistantResponse(
        text: String,
        intent: ParsedIntent? = null,
        result: ActionResult? = null,
        language: String? = null
    ): Long {
        val entity = MessageEntity(
            text = text,
            isUser = false,
            actionType = intent?.type?.name,
            actionTarget = intent?.target ?: intent?.app ?: result?.launchedTarget,
            actionSuccess = result?.success,
            language = language
        )
        return messageDao.insertMessage(entity)
    }

    suspend fun recordInteraction(
        userQuery: String,
        assistantResponse: String,
        intent: ParsedIntent? = null,
        result: ActionResult? = null,
        executionSource: String = "LOCAL",
        contextEntity: String? = null,
        contextTopic: String? = null,
        contextVariables: String? = null,
        language: String? = null,
        latencyMs: Long = 0L
    ): Long {
        val entity = InteractionHistoryEntity(
            userQuery = userQuery,
            assistantResponse = assistantResponse,
            intentType = intent?.type?.name,
            intentTarget = intent?.target ?: intent?.app ?: result?.launchedTarget,
            actionSuccess = result?.success ?: true,
            executionSource = executionSource,
            contextEntity = contextEntity,
            contextTopic = contextTopic,
            contextVariables = contextVariables,
            language = language,
            latencyMs = latencyMs
        )
        return interactionHistoryDao.insertInteraction(entity)
    }

    suspend fun clearHistory() {
        messageDao.clearAllMessages()
    }

    suspend fun clearInteractionHistory() {
        interactionHistoryDao.clearAllHistory()
    }

    suspend fun setPreference(key: String, value: String) {
        preferenceDao.setPreference(UserPreferenceEntity(key = key, value = value))
        prefs.edit().putString(key, value).apply()
    }

    suspend fun getPreference(key: String): String? {
        return preferenceDao.getPreference(key) ?: prefs.getString(key, null)
    }

    suspend fun getSavedLanguage(): String {
        return getPreference(KEY_LANGUAGE) ?: "hi_en"
    }

    fun getCustomApiKey(): String {
        return prefs.getString(PREF_CUSTOM_API_KEY, "") ?: ""
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(PREF_CUSTOM_API_KEY, key).apply()
    }

    fun isForegroundServiceEnabled(): Boolean {
        return prefs.getBoolean(PREF_SERVICE_ENABLED, true)
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SERVICE_ENABLED, enabled).apply()
    }

    fun isHandsFreeVoiceEnabled(): Boolean {
        return prefs.getBoolean(PREF_HANDS_FREE_ENABLED, false)
    }

    fun setHandsFreeVoiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_HANDS_FREE_ENABLED, enabled).apply()
    }

    fun getVoiceLanguage(): String {
        return prefs.getString(PREF_VOICE_LANGUAGE, "hi_en") ?: "hi_en"
    }

    fun setVoiceLanguage(languageCode: String) {
        prefs.edit().putString(PREF_VOICE_LANGUAGE, languageCode).apply()
    }

    fun isVoiceOutputEnabled(): Boolean {
        return prefs.getBoolean(PREF_VOICE_OUTPUT_ENABLED, true)
    }

    fun setVoiceOutputEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_VOICE_OUTPUT_ENABLED, enabled).apply()
    }

    fun getTtsSpeechRate(): Float {
        return prefs.getFloat(PREF_TTS_SPEECH_RATE, 1.0f)
    }

    fun setTtsSpeechRate(rate: Float) {
        prefs.edit().putFloat(PREF_TTS_SPEECH_RATE, rate).apply()
    }

    fun getTtsPitch(): Float {
        return prefs.getFloat(PREF_TTS_PITCH, 1.0f)
    }

    fun setTtsPitch(pitch: Float) {
        prefs.edit().putFloat(PREF_TTS_PITCH, pitch).apply()
    }
}
