package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 10): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM user_preferences")
    fun getAllPreferences(): Flow<List<UserPreferenceEntity>>

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPreference(preference: UserPreferenceEntity)

    @Query("DELETE FROM user_preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)
}

@Dao
interface InteractionHistoryDao {
    @Query("SELECT * FROM interaction_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<InteractionHistoryEntity>>

    @Query("SELECT * FROM interaction_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentInteractions(limit: Int = 20): List<InteractionHistoryEntity>

    @Query("SELECT * FROM interaction_history WHERE contextTopic = :topic ORDER BY timestamp DESC")
    fun getHistoryByTopic(topic: String): Flow<List<InteractionHistoryEntity>>

    @Query("SELECT * FROM interaction_history WHERE userQuery LIKE '%' || :query || '%' OR assistantResponse LIKE '%' || :query || '%'")
    fun searchHistory(query: String): Flow<List<InteractionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: InteractionHistoryEntity): Long

    @Query("DELETE FROM interaction_history WHERE id = :id")
    suspend fun deleteInteraction(id: Long)

    @Query("DELETE FROM interaction_history")
    suspend fun clearAllHistory()
}

@Dao
interface ExperienceDao {
    @Query("SELECT * FROM experiences ORDER BY lastUsedTimestamp DESC")
    fun getAllExperiences(): Flow<List<ExperienceEntity>>

    @Query("SELECT * FROM experiences WHERE normalizedCommand = :normalized LIMIT 1")
    suspend fun findExactExperience(normalized: String): ExperienceEntity?

    @Query("SELECT * FROM experiences WHERE normalizedCommand LIKE '%' || :keyword || '%' OR userCommand LIKE '%' || :keyword || '%' ORDER BY confidence DESC, successCount DESC LIMIT :limit")
    suspend fun searchExperiences(keyword: String, limit: Int = 5): List<ExperienceEntity>

    @Query("SELECT * FROM experiences WHERE intentType = :intent ORDER BY confidence DESC, successCount DESC LIMIT :limit")
    suspend fun getExperiencesByIntent(intent: String, limit: Int = 5): List<ExperienceEntity>

    @Query("SELECT * FROM experiences WHERE targetApp = :targetApp ORDER BY confidence DESC LIMIT :limit")
    suspend fun getExperiencesForApp(targetApp: String, limit: Int = 5): List<ExperienceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperience(experience: ExperienceEntity): Long

    @Update
    suspend fun updateExperience(experience: ExperienceEntity)

    @Query("DELETE FROM experiences WHERE id = :id")
    suspend fun deleteExperience(id: Long)

    @Query("DELETE FROM experiences")
    suspend fun clearAllExperiences()

    @Query("SELECT COUNT(*) FROM experiences")
    fun countExperiences(): Flow<Int>

    @Query("DELETE FROM experiences WHERE failureCount > 3 AND confidence < 0.3")
    suspend fun pruneBadExperiences(): Int
}

@Dao
interface LearnedSkillDao {
    @Query("SELECT * FROM learned_skills ORDER BY lastUsedAt DESC")
    fun getAllSkills(): Flow<List<LearnedSkillEntity>>

    @Query("SELECT * FROM learned_skills WHERE isEnabled = 1 ORDER BY confidence DESC, successCount DESC")
    suspend fun getEnabledSkills(): List<LearnedSkillEntity>

    @Query("SELECT * FROM learned_skills WHERE skillName = :name LIMIT 1")
    suspend fun getSkillByName(name: String): LearnedSkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: LearnedSkillEntity): Long

    @Update
    suspend fun updateSkill(skill: LearnedSkillEntity)

    @Query("DELETE FROM learned_skills WHERE id = :id")
    suspend fun deleteSkill(id: Long)

    @Query("DELETE FROM learned_skills")
    suspend fun clearAllSkills()

    @Query("SELECT COUNT(*) FROM learned_skills")
    fun countSkills(): Flow<Int>
}

@Dao
interface FailedStrategyDao {
    @Query("SELECT * FROM failed_strategies ORDER BY lastFailedTimestamp DESC")
    fun getAllFailedStrategies(): Flow<List<FailedStrategyEntity>>

    @Query("SELECT * FROM failed_strategies WHERE pattern = :pattern AND (:screenPackage IS NULL OR screenPackage = :screenPackage) LIMIT 1")
    suspend fun findFailure(pattern: String, screenPackage: String?): FailedStrategyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFailedStrategy(failed: FailedStrategyEntity): Long

    @Update
    suspend fun updateFailedStrategy(failed: FailedStrategyEntity)

    @Query("DELETE FROM failed_strategies WHERE id = :id")
    suspend fun deleteFailedStrategy(id: Long)

    @Query("DELETE FROM failed_strategies")
    suspend fun clearAllFailedStrategies()
}

@Dao
interface LearnedFactDao {
    @Query("SELECT * FROM learned_facts ORDER BY timestamp DESC")
    fun getAllFacts(): Flow<List<LearnedFactEntity>>

    @Query("SELECT * FROM learned_facts WHERE category = :category AND `key` = :key LIMIT 1")
    suspend fun getFact(category: String, key: String): LearnedFactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: LearnedFactEntity): Long

    @Query("DELETE FROM learned_facts WHERE id = :id")
    suspend fun deleteFact(id: Long)

    @Query("DELETE FROM learned_facts")
    suspend fun clearAllFacts()
}
