package com.example.brain

import android.util.Log
import com.example.data.ExperienceEntity
import com.example.data.FailedStrategyEntity
import com.example.data.LearnedSkillEntity
import com.example.data.MemoryRepository
import com.example.models.ActionChainResult
import com.example.models.ActionResult
import com.example.models.DecisionSource
import com.example.models.ExecutionDecision
import com.example.models.ParsedIntent
import com.example.models.ScreenSnapshot
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class LearningEngine(
    private val memoryRepository: MemoryRepository,
    private val confidenceEngine: ConfidenceEngine = ConfidenceEngine()
) {

    companion object {
        private const val TAG = "LearningEngine"
    }

    suspend fun processCompletedRequest(
        userQuery: String,
        normalizedQuery: String,
        decision: ExecutionDecision,
        actionResult: ActionResult,
        chainResult: ActionChainResult? = null,
        screenSnapshot: ScreenSnapshot? = null,
        isCancelled: Boolean = false
    ) {
        if (isCancelled) {
            Log.d(TAG, "[LEARNING] Request was cancelled, skipping learning.")
            return
        }

        if (!memoryRepository.isAutoLearningEnabled()) {
            Log.d(TAG, "[LEARNING] Automatic learning is disabled by user.")
            return
        }

        if (PrivacyFilter.isSensitive(userQuery)) {
            Log.d(TAG, "[LEARNING] Query contains sensitive or private data, refusing to learn.")
            return
        }

        // Only verified outcomes should strengthen skill confidence; intermediate or partial action success is not request success
        val success = actionResult.success && (chainResult?.success ?: true) && !actionResult.partial && actionResult.isVerified

        if (success) {
            handleSuccess(userQuery, normalizedQuery, decision, actionResult, chainResult, screenSnapshot)
        } else {
            handleFailure(userQuery, normalizedQuery, decision, actionResult, chainResult, screenSnapshot)
        }
    }

    suspend fun recordCorrection(
        userQuery: String,
        normalizedQuery: String,
        wrongIntentType: String,
        userCorrection: String
    ) {
        Log.w(TAG, "[LEARNING] User corrected previous command: '$normalizedQuery' (wrongly parsed as $wrongIntentType)")
        val existing = memoryRepository.findExactExperience(normalizedQuery)
        if (existing != null) {
            val updated = existing.copy(
                confidence = max(0.0f, existing.confidence - 0.40f),
                failureCount = existing.failureCount + 1,
                resultSuccess = false,
                failureReason = "User corrected misclassification: $userCorrection",
                lastUsedTimestamp = System.currentTimeMillis()
            )
            memoryRepository.updateExperience(updated)
        }

        val failed = FailedStrategyEntity(
            pattern = normalizedQuery,
            failedAction = wrongIntentType,
            target = null,
            screenPackage = null,
            reason = "Misinterpreted intent: $userCorrection",
            lastFailedTimestamp = System.currentTimeMillis()
        )
        memoryRepository.recordFailedStrategy(failed)
    }

    private suspend fun handleSuccess(
        userQuery: String,
        normalizedQuery: String,
        decision: ExecutionDecision,
        actionResult: ActionResult,
        chainResult: ActionChainResult?,
        screenSnapshot: ScreenSnapshot?
    ) {
        val intent = decision.intent
        val targetApp = intent.app ?: actionResult.launchedTarget
        val targetPackage = intent.target ?: screenSnapshot?.packageName

        // 1. Check existing experience
        val existing = memoryRepository.findExactExperience(normalizedQuery)

        if (existing != null) {
            val updatedSuccessCount = existing.successCount + 1
            val newConfidence = min(1.0f, existing.confidence + 0.05f)

            val updated = existing.copy(
                successCount = updatedSuccessCount,
                confidence = newConfidence,
                lastUsedTimestamp = System.currentTimeMillis(),
                resultSuccess = true,
                failureReason = null
            )
            memoryRepository.updateExperience(updated)
            Log.d(TAG, "[LEARNING] Strengthened existing experience: '$normalizedQuery' (confidence: $newConfidence, count: $updatedSuccessCount)")

            // Check if this workflow repeated multiple times -> create reusable skill
            if (updatedSuccessCount >= 2 && intent.app != null) {
                maybeCreateOrUpdateSkill(intent, userQuery, normalizedQuery, updatedSuccessCount)
            }
        } else {
            val initialConfidence = when (decision.source) {
                DecisionSource.LOCAL_PARSER -> 0.90f
                DecisionSource.LEARNED_SKILL -> 0.85f
                DecisionSource.EXPERIENCE_MEMORY -> 0.80f
                DecisionSource.GEMINI_FALLBACK -> 0.75f
                else -> 0.70f
            }

            val actionsJson = chainResult?.let { chain ->
                val arr = JSONArray()
                chain.stepResults.forEach {
                    arr.put(JSONObject().apply {
                        put("stepId", it.stepId)
                        put("actionType", it.actionType)
                        put("success", it.success)
                    })
                }
                arr.toString()
            }

            val experience = ExperienceEntity(
                userCommand = userQuery,
                normalizedCommand = normalizedQuery,
                intentType = intent.type.name,
                targetApp = targetApp,
                targetPackage = targetPackage,
                screenPackage = screenSnapshot?.packageName,
                actionSequenceJson = actionsJson,
                resultSuccess = true,
                confidence = initialConfidence,
                successCount = 1,
                failureCount = 0,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            memoryRepository.saveExperience(experience)
            Log.d(TAG, "[LEARNING] Created new validated experience: '$normalizedQuery' with confidence $initialConfidence")
        }
    }

    private suspend fun handleFailure(
        userQuery: String,
        normalizedQuery: String,
        decision: ExecutionDecision,
        actionResult: ActionResult,
        chainResult: ActionChainResult?,
        screenSnapshot: ScreenSnapshot?
    ) {
        val failureReason = actionResult.error ?: chainResult?.failureReason ?: "Action unsuccessful"

        val existing = memoryRepository.findExactExperience(normalizedQuery)
        if (existing != null) {
            val newConfidence = max(0.0f, existing.confidence - 0.20f)
            val updated = existing.copy(
                failureCount = existing.failureCount + 1,
                confidence = newConfidence,
                lastUsedTimestamp = System.currentTimeMillis(),
                resultSuccess = false,
                failureReason = failureReason
            )
            memoryRepository.updateExperience(updated)
            Log.w(TAG, "[LEARNING] Penalized experience '$normalizedQuery' due to failure: $failureReason (new confidence: $newConfidence)")
        }

        // Record failed strategy to prevent repeating this exact failed approach
        val failedAction = chainResult?.stepResults?.firstOrNull { !it.success }?.actionType ?: decision.intent.type.name
        val failed = FailedStrategyEntity(
            pattern = normalizedQuery,
            failedAction = failedAction,
            target = decision.intent.target ?: decision.intent.app,
            screenPackage = screenSnapshot?.packageName,
            reason = failureReason,
            lastFailedTimestamp = System.currentTimeMillis()
        )
        memoryRepository.recordFailedStrategy(failed)
        Log.w(TAG, "[LEARNING] Recorded failed strategy: action='$failedAction' on pattern='$normalizedQuery'")
    }

    private suspend fun maybeCreateOrUpdateSkill(
        intent: ParsedIntent,
        userQuery: String,
        normalizedQuery: String,
        successCount: Int
    ) {
        val skillName = "SKILL_${intent.type.name}_${(intent.app ?: "ACTION").uppercase()}"
        val existingSkill = memoryRepository.getSkillByName(skillName)

        val triggerList = mutableListOf(normalizedQuery, userQuery.lowercase().trim())
        val defaultActions = JSONArray().apply {
            put(JSONObject().apply {
                put("stepId", 1)
                put("actionType", intent.type.name)
                put("target", intent.target ?: intent.app)
                put("value", intent.query)
            })
        }

        if (existingSkill != null) {
            val triggers = try {
                val arr = JSONArray(existingSkill.triggerPatternsJson)
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                set.addAll(triggerList)
                JSONArray(set.toList()).toString()
            } catch (e: Exception) {
                JSONArray(triggerList).toString()
            }

            val updatedSkill = existingSkill.copy(
                triggerPatternsJson = triggers,
                successCount = existingSkill.successCount + 1,
                confidence = min(1.0f, existingSkill.confidence + 0.05f),
                lastUsedAt = System.currentTimeMillis(),
                version = existingSkill.version + 1
            )
            memoryRepository.updateSkill(updatedSkill)
            Log.d(TAG, "[LEARNING] Updated Skill '$skillName' to v${updatedSkill.version} with confidence ${updatedSkill.confidence}")
        } else {
            val skill = LearnedSkillEntity(
                skillName = skillName,
                triggerPatternsJson = JSONArray(triggerList).toString(),
                requiredApp = intent.target ?: intent.app,
                actionsJson = defaultActions.toString(),
                verificationRule = "LAUNCH_SUCCESS",
                confidence = 0.85f,
                successCount = successCount,
                version = 1,
                createdAt = System.currentTimeMillis(),
                lastUsedAt = System.currentTimeMillis()
            )
            memoryRepository.saveSkill(skill)
            Log.d(TAG, "[LEARNING] Successfully synthesized new reusable skill: '$skillName' (v1)")
        }
    }
}
