package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.brain.PrivacyFilter
import com.example.models.ActionResult
import com.example.models.ParsedIntent
import kotlinx.coroutines.flow.Flow

class MemoryRepository(
    private val messageDao: MessageDao,
    private val preferenceDao: PreferenceDao,
    private val interactionHistoryDao: InteractionHistoryDao,
    private val experienceDao: ExperienceDao,
    private val learnedSkillDao: LearnedSkillDao,
    private val failedStrategyDao: FailedStrategyDao,
    private val learnedFactDao: LearnedFactDao,
    context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("myra_settings", Context.MODE_PRIVATE)

    val messages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    val allPreferences: Flow<List<UserPreferenceEntity>> = preferenceDao.getAllPreferences()
    val interactionHistory: Flow<List<InteractionHistoryEntity>> = interactionHistoryDao.getAllHistory()

    val experiences: Flow<List<ExperienceEntity>> = experienceDao.getAllExperiences()
    val learnedSkills: Flow<List<LearnedSkillEntity>> = learnedSkillDao.getAllSkills()
    val failedStrategies: Flow<List<FailedStrategyEntity>> = failedStrategyDao.getAllFailedStrategies()
    val learnedFacts: Flow<List<LearnedFactEntity>> = learnedFactDao.getAllFacts()

    val experienceCount: Flow<Int> = experienceDao.countExperiences()
    val skillCount: Flow<Int> = learnedSkillDao.countSkills()

    companion object {
        const val KEY_LANGUAGE = "preferred_language"
        const val PREF_CUSTOM_API_KEY = "custom_gemini_api_key"
        const val PREF_SERVICE_ENABLED = "foreground_service_enabled"
        const val PREF_HANDS_FREE_ENABLED = "hands_free_voice_enabled"
        const val PREF_VOICE_LANGUAGE = "voice_speech_language"
        const val PREF_VOICE_OUTPUT_ENABLED = "voice_output_enabled"
        const val PREF_TTS_SPEECH_RATE = "tts_speech_rate"
        const val PREF_TTS_PITCH = "tts_pitch"
        const val PREF_AUTO_LEARNING_ENABLED = "auto_learning_enabled"
        const val PREF_GEMINI_FALLBACK_ENABLED = "gemini_fallback_enabled"
    }

    suspend fun saveUserMessage(text: String, language: String? = null): Long {
        val safeText = if (PrivacyFilter.isSensitive(text)) PrivacyFilter.redactSensitive(text) else text
        val entity = MessageEntity(
            text = safeText,
            isUser = true,
            language = language
        )
        return messageDao.insertMessage(entity)
    }

    suspend fun saveAssistantResponse(
        text: String,
        intent: ParsedIntent? = null,
        result: ActionResult? = null,
        language: String? = null,
        executionSource: String = "LOCAL",
        confidence: Float? = null
    ): Long {
        val entity = MessageEntity(
            text = text,
            isUser = false,
            actionType = intent?.type?.name,
            actionTarget = intent?.target ?: intent?.app ?: result?.launchedTarget,
            actionSuccess = result?.success,
            language = language,
            executionSource = executionSource,
            confidence = confidence
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
        if (PrivacyFilter.isSensitive(userQuery)) {
            return -1L
        }
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

    // --- EXPERIENCE MEMORY METHODS ---

    suspend fun findExactExperience(normalized: String): ExperienceEntity? {
        return experienceDao.findExactExperience(normalized)
    }

    suspend fun searchExperiences(keyword: String, limit: Int = 5): List<ExperienceEntity> {
        return experienceDao.searchExperiences(keyword, limit)
    }

    suspend fun getExperiencesByIntent(intent: String, limit: Int = 5): List<ExperienceEntity> {
        return experienceDao.getExperiencesByIntent(intent, limit)
    }

    suspend fun saveExperience(experience: ExperienceEntity): Long {
        if (PrivacyFilter.isSensitive(experience.userCommand)) {
            return -1L
        }
        return experienceDao.insertExperience(experience)
    }

    suspend fun updateExperience(experience: ExperienceEntity) {
        experienceDao.updateExperience(experience)
    }

    suspend fun deleteExperience(id: Long) {
        experienceDao.deleteExperience(id)
    }

    suspend fun clearExperiences() {
        experienceDao.clearAllExperiences()
    }

    suspend fun pruneBadExperiences(): Int {
        return experienceDao.pruneBadExperiences()
    }

    // --- LEARNED SKILLS METHODS ---

    suspend fun getEnabledSkills(): List<LearnedSkillEntity> {
        return learnedSkillDao.getEnabledSkills()
    }

    suspend fun getSkillByName(name: String): LearnedSkillEntity? {
        return learnedSkillDao.getSkillByName(name)
    }

    suspend fun saveSkill(skill: LearnedSkillEntity): Long {
        return learnedSkillDao.insertSkill(skill)
    }

    suspend fun updateSkill(skill: LearnedSkillEntity) {
        learnedSkillDao.updateSkill(skill)
    }

    suspend fun deleteSkill(id: Long) {
        learnedSkillDao.deleteSkill(id)
    }

    suspend fun clearSkills() {
        learnedSkillDao.clearAllSkills()
    }

    // --- FAILED STRATEGY METHODS ---

    suspend fun findFailedStrategy(pattern: String, screenPackage: String?): FailedStrategyEntity? {
        return failedStrategyDao.findFailure(pattern, screenPackage)
    }

    suspend fun recordFailedStrategy(failed: FailedStrategyEntity): Long {
        val existing = failedStrategyDao.findFailure(failed.pattern, failed.screenPackage)
        return if (existing != null) {
            val updated = existing.copy(
                failureCount = existing.failureCount + 1,
                lastFailedTimestamp = System.currentTimeMillis(),
                reason = failed.reason ?: existing.reason
            )
            failedStrategyDao.updateFailedStrategy(updated)
            existing.id
        } else {
            failedStrategyDao.insertFailedStrategy(failed)
        }
    }

    suspend fun clearFailedStrategies() {
        failedStrategyDao.clearAllFailedStrategies()
    }

    // --- LEARNED FACTS METHODS ---

    suspend fun getFact(category: String, key: String): LearnedFactEntity? {
        return learnedFactDao.getFact(category, key)
    }

    suspend fun saveFact(category: String, key: String, value: String, confidence: Float = 0.9f): Long {
        if (PrivacyFilter.isSensitive(value) || PrivacyFilter.isSensitive(key)) {
            return -1L
        }
        val fact = LearnedFactEntity(
            category = category,
            key = key,
            value = value,
            confidence = confidence
        )
        return learnedFactDao.insertFact(fact)
    }

    suspend fun clearFacts() {
        learnedFactDao.clearAllFacts()
    }

    // --- COMPLETE RESET OF LEARNED KNOWLEDGE ---

    suspend fun resetAllLearnedKnowledge() {
        experienceDao.clearAllExperiences()
        learnedSkillDao.clearAllSkills()
        failedStrategyDao.clearAllFailedStrategies()
        learnedFactDao.clearAllFacts()
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

    fun setCustomApiKey(key: String): Boolean {
        return prefs.edit().putString(PREF_CUSTOM_API_KEY, key.trim()).commit()
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

    fun isAutoLearningEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_LEARNING_ENABLED, true)
    }

    fun setAutoLearningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_LEARNING_ENABLED, enabled).apply()
    }

    fun isGeminiFallbackEnabled(): Boolean {
        return prefs.getBoolean(PREF_GEMINI_FALLBACK_ENABLED, true)
    }

    fun setGeminiFallbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_GEMINI_FALLBACK_ENABLED, enabled).apply()
    }
}
