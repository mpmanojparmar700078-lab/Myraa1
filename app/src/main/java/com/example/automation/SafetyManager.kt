package com.example.automation

import android.util.Log
import com.example.models.ScreenState

sealed class SafetyCheckResult {
    data object Safe : SafetyCheckResult()
    data class Blocked(val reason: String) : SafetyCheckResult()
}

/**
 * Enforces safety boundaries, loop protection, sensitive data redaction,
 * and confidence thresholds across autonomous device interactions.
 */
class SafetyManager {

    companion object {
        private const val TAG = "SafetyManager"
        const val MIN_ACTION_CONFIDENCE = 0.65f
    }

    /**
     * Validates if a proposed action is safe to execute.
     */
    fun validateAction(
        task: AutonomousTask,
        action: PlannedAction,
        currentScreen: ScreenState?
    ): SafetyCheckResult {
        // 1. Timeout Check
        if (System.currentTimeMillis() - task.startTime > task.timeoutMs) {
            return SafetyCheckResult.Blocked("Task exceeded maximum execution timeout (${task.timeoutMs / 1000}s). Safely stopping.")
        }

        // 2. Maximum Steps Check
        if (task.history.size >= task.maxSteps) {
            return SafetyCheckResult.Blocked("Task reached maximum safety step limit (${task.maxSteps} actions). Stopping to prevent runaway execution.")
        }

        // 3. Confidence Threshold Check
        if (action.confidence < MIN_ACTION_CONFIDENCE) {
            return SafetyCheckResult.Blocked("Action confidence too low (${(action.confidence * 100).toInt()}%). Requires manual user verification.")
        }

        // 4. Password / Sensitive Field Check
        if (action.type == PlannedActionType.TYPE_TEXT && currentScreen != null) {
            val targetedPasswordNode = currentScreen.elements.any { it.isPassword && it.isFocused }
            if (targetedPasswordNode) {
                return SafetyCheckResult.Blocked("Active field is marked as a secure password/credential. Automated text entry is blocked for security.")
            }
        }

        // 5. Loop Protection (Stuck-state detection)
        if (task.history.size >= 3) {
            val lastThree = task.history.takeLast(3)
            val allSameType = lastThree.all { it.action.type == action.type && it.action.target == action.target }
            val noneVerified = lastThree.all { !it.verified }
            if (allSameType && noneVerified) {
                return SafetyCheckResult.Blocked("Detected repeating stuck loop on '${action.target}'. Aborting task safely.")
            }
        }

        return SafetyCheckResult.Safe
    }
}
