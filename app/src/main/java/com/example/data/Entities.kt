package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assistant_messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null,
    val actionTarget: String? = null,
    val actionSuccess: Boolean? = null,
    val language: String? = null
)

@Entity(tableName = "user_preferences")
data class UserPreferenceEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "interaction_history")
data class InteractionHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userQuery: String,
    val assistantResponse: String,
    val intentType: String? = null,
    val intentTarget: String? = null,
    val actionSuccess: Boolean = true,
    val executionSource: String = "LOCAL",
    val contextEntity: String? = null,
    val contextTopic: String? = null,
    val contextVariables: String? = null,
    val language: String? = null,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

