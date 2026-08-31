package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM assistant_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM assistant_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 10): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("DELETE FROM assistant_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM assistant_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)
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
    @Query("SELECT * FROM interaction_history ORDER BY timestamp DESC, id DESC")
    fun getAllHistory(): Flow<List<InteractionHistoryEntity>>

    @Query("SELECT * FROM interaction_history ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentInteractions(limit: Int = 20): List<InteractionHistoryEntity>

    @Query("SELECT * FROM interaction_history WHERE contextTopic = :topic ORDER BY timestamp DESC, id DESC")
    fun getHistoryByTopic(topic: String): Flow<List<InteractionHistoryEntity>>

    @Query("SELECT * FROM interaction_history WHERE userQuery LIKE '%' || :query || '%' OR assistantResponse LIKE '%' || :query || '%' OR contextEntity LIKE '%' || :query || '%' ORDER BY timestamp DESC, id DESC")
    fun searchHistory(query: String): Flow<List<InteractionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: InteractionHistoryEntity): Long

    @Query("DELETE FROM interaction_history WHERE id = :id")
    suspend fun deleteInteraction(id: Long)

    @Query("DELETE FROM interaction_history")
    suspend fun clearAllHistory()
}


