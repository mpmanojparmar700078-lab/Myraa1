package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null,
    val actionTarget: String? = null,
    val actionSuccess: Boolean? = null,
    val language: String? = null,
    val executionSource: String? = "LOCAL",
    val confidence: Float? = null
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
    val id: Long = 0L,
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

@Entity(
    tableName = "experiences",
    indices = [
        Index(value = ["normalizedCommand"]),
        Index(value = ["intentType"]),
        Index(value = ["targetApp"])
    ]
)
data class ExperienceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userCommand: String,
    val normalizedCommand: String,
    val intentType: String,
    val targetApp: String? = null,
    val targetPackage: String? = null,
    val screenPackage: String? = null,
    val actionSequenceJson: String? = null,
    val resultSuccess: Boolean = true,
    val failureReason: String? = null,
    val confidence: Float = 0.8f,
    val successCount: Int = 1,
    val failureCount: Int = 0,
    val skillId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "learned_skills",
    indices = [Index(value = ["skillName"], unique = true)]
)
data class LearnedSkillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val skillName: String,
    val triggerPatternsJson: String, // JSON array string e.g. ["youtube search", "youtube par khojo"]
    val requiredApp: String? = null,
    val actionsJson: String, // JSON array of ActionStep
    val conditionsJson: String? = null,
    val verificationRule: String? = null,
    val confidence: Float = 0.85f,
    val successCount: Int = 1,
    val failureCount: Int = 0,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true
)

@Entity(
    tableName = "failed_strategies",
    indices = [Index(value = ["pattern"])]
)
data class FailedStrategyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pattern: String,
    val failedAction: String,
    val target: String? = null,
    val screenPackage: String? = null,
    val reason: String? = null,
    val failureCount: Int = 1,
    val lastFailedTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "learned_facts",
    indices = [Index(value = ["category", "key"], unique = true)]
)
data class LearnedFactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val category: String, // "USER_PREFERENCE", "FACT", "SYSTEM_KNOWLEDGE"
    val key: String,
    val value: String,
    val confidence: Float = 0.9f,
    val timestamp: Long = System.currentTimeMillis()
)
