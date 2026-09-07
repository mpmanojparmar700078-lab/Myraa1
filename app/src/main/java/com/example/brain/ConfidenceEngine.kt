package com.example.brain

import com.example.data.ExperienceEntity
import com.example.data.LearnedSkillEntity
import com.example.models.LocalCommandResult
import kotlin.math.max
import kotlin.math.min

class ConfidenceEngine {

    companion object {
        const val THRESHOLD_LOCAL_EXECUTION = 0.70f
        const val THRESHOLD_HIGHLY_RELIABLE = 0.85f
    }

    fun calculateLocalParserConfidence(result: LocalCommandResult): Float {
        if (!result.handled || result.intent == null) return 0.0f
        return result.intent.confidence
    }

    fun calculateSkillConfidence(skill: LearnedSkillEntity): Float {
        if (!skill.isEnabled) return 0.0f
        val base = skill.confidence
        val successBonus = min(0.15f, skill.successCount * 0.03f)
        val failurePenalty = skill.failureCount * 0.20f
        return max(0.0f, min(1.0f, base + successBonus - failurePenalty))
    }

    fun calculateExperienceConfidence(experience: ExperienceEntity): Float {
        val base = experience.confidence
        val successBonus = min(0.15f, experience.successCount * 0.03f)
        val failurePenalty = experience.failureCount * 0.20f
        return max(0.0f, min(1.0f, base + successBonus - failurePenalty))
    }

    fun isConfidentForLocalExecution(confidence: Float): Boolean {
        return confidence >= THRESHOLD_LOCAL_EXECUTION
    }
}
