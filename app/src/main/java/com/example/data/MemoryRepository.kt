package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.models.ActionResult
import com.example.models.ParsedIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

class MemoryRepository(
    private val messageDao: MessageDao,
    private val preferenceDao: PreferenceDao,
    private val interactionHistoryDao: InteractionHistoryDao? = null,
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("myra_settings", Context.MODE_PRIVATE)

    companion object {
        const val PREF_CUSTOM_API_KEY = "custom_gemini_api_key"
        const val PREF_SERVICE_ENABLED = "foreground_service_enabled"
        const val KEY_LANGUAGE = "preferred_language"
        const val PREF_HANDS_FREE_ENABLED = "hands_free_voice_enabled"
        const val PREF_VOICE_LANGUAGE = "voice_speech_language"
        const val PREF_VOICE_OUTPUT_ENABLED = "voice_output_enabled"
        const val PREF_TTS_SPEECH_RATE = "tts_speech_rate"
        const val PREF_TTS_PITCH = "tts_pitch"
    }

    val messages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val allPreferences: Flow<List<UserPreferenceEntity>> = preferenceDao.getAllPreferences()
    val interactionHistory: Flow<List<InteractionHistoryEntity>> =
        interactionHistoryDao?.getAllHistory() ?: emptyFlow()

    suspend fun saveUserMessage(text: String): Long {
        val entity = MessageEntity(
            text = text,
            isUser = true,
            timestamp = System.currentTimeMillis()
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
            timestamp = System.currentTimeMillis(),
            actionType = intent?.type?.name,
            actionTarget = intent?.app ?: intent?.query ?: intent?.target ?: intent?.key,
            actionSuccess = result?.success,
            language = language
        )
        return messageDao.insertMessage(entity)
    }

    /**
     * Stores structured user interaction and context in the Room history table.
     */
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
        if (interactionHistoryDao == null) return -1L
        val historyItem = InteractionHistoryEntity(
            userQuery = userQuery,
            assistantResponse = assistantResponse,
            intentType = intent?.type?.name,
            intentTarget = intent?.app ?: intent?.query ?: intent?.target ?: intent?.key,
            actionSuccess = result?.success ?: true,
            executionSource = executionSource,
            contextEntity = contextEntity,
            contextTopic = contextTopic,
            contextVariables = contextVariables,
            language = language,
            latencyMs = latencyMs,
            timestamp = System.currentTimeMillis()
        )
        return interactionHistoryDao.insertInteraction(historyItem)
    }

    suspend fun getRecentInteractions(limit: Int = 20): List<InteractionHistoryEntity> {
        return interactionHistoryDao?.getRecentInteractions(limit) ?: emptyList()
    }

    fun searchInteractions(query: String): Flow<List<InteractionHistoryEntity>> {
        return interactionHistoryDao?.searchHistory(query) ?: emptyFlow()
    }

    fun getInteractionsByTopic(topic: String): Flow<List<InteractionHistoryEntity>> {
        return interactionHistoryDao?.getHistoryByTopic(topic) ?: emptyFlow()
    }

    suspend fun deleteInteraction(id: Long) {
        interactionHistoryDao?.deleteInteraction(id)
    }

    suspend fun clearInteractionHistory() {
        interactionHistoryDao?.clearAllHistory()
    }

    suspend fun clearHistory() {
        messageDao.clearAllMessages()
        interactionHistoryDao?.clearAllHistory()
    }

    suspend fun setPreference(key: String, value: String) {
        preferenceDao.setPreference(UserPreferenceEntity(key = key, value = value, updatedAt = System.currentTimeMillis()))
    }

    suspend fun getPreference(key: String): String? {
        return preferenceDao.getPreference(key)
    }

    suspend fun getSavedLanguage(): String {
        return preferenceDao.getPreference(KEY_LANGUAGE) ?: "auto"
    }

    suspend fun getRecentMessages(limit: Int = 10): List<MessageEntity> {
        return messageDao.getRecentMessages(limit)
    }

    suspend fun buildMemoryContextString(): String {
        val lang = preferenceDao.getPreference(KEY_LANGUAGE) ?: "auto"
        val recentMsgs = messageDao.getRecentMessages(10).reversed()
        val recentInteractions = interactionHistoryDao?.getRecentInteractions(5)?.reversed() ?: emptyList()

        val sb = StringBuilder()
        sb.append("Current saved user preferences:\n")
        sb.append("- Preferred language: $lang\n")

        if (recentInteractions.isNotEmpty()) {
            sb.append("\nPAST INTERACTION CONTEXT & ENTITIES:\n")
            for (item in recentInteractions) {
                val entityInfo = item.contextEntity?.let { " [Active Entity: $it]" } ?: ""
                val topicInfo = item.contextTopic?.let { " [Topic: $it]" } ?: ""
                sb.append("- User: ${item.userQuery} -> Myra (${item.executionSource}): ${item.assistantResponse}$entityInfo$topicInfo\n")
            }
        }

        if (recentMsgs.isNotEmpty()) {
            sb.append("\nRECENT CONVERSATION HISTORY (In order):\n")
            for (msg in recentMsgs) {
                val role = if (msg.isUser) "User" else "Myra"
                sb.append("- $role: ${msg.text}\n")
            }
        }
        return sb.toString()
    }

    fun getCustomApiKey(): String {
        return prefs.getString(PREF_CUSTOM_API_KEY, "") ?: ""
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(PREF_CUSTOM_API_KEY, key.trim()).apply()
    }

    fun isForegroundServiceEnabled(): Boolean {
        return prefs.getBoolean(PREF_SERVICE_ENABLED, true)
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SERVICE_ENABLED, enabled).apply()
    }

    fun isHandsFreeVoiceEnabled(): Boolean {
        return prefs.getBoolean(PREF_HANDS_FREE_ENABLED, true)
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

