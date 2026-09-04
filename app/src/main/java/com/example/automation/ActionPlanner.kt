package com.example.automation

import com.example.device.AppCategory
import com.example.device.DeviceAppContext
import com.example.models.ScreenState
import com.example.vision.DetectedTarget
import com.example.vision.TargetDetector

/**
 * Action Planner determining the next step in a multi-step autonomous task.
 * Prioritizes semantic accessible UI elements before falling back to coordinates.
 */
class ActionPlanner(
    private val targetDetector: TargetDetector = TargetDetector()
) {

    /**
     * Determines the next action to perform to advance the user's goal.
     */
    fun planNextAction(
        task: AutonomousTask,
        currentScreen: ScreenState?,
        appContext: DeviceAppContext
    ): PlannedAction? {
        val goalLower = task.goal.lowercase()

        // 1. App Launching Phase
        if (task.targetApp != null && appContext.packageName != task.targetApp && task.history.isEmpty()) {
            return PlannedAction(
                type = PlannedActionType.OPEN_APP,
                target = task.targetApp,
                confidence = 0.99f,
                reason = "Target application is not yet active in foreground."
            )
        }

        // 2. Game Mode Action Planning
        if (appContext.category == AppCategory.GAME || appContext.isGame) {
            return PlannedAction(
                type = PlannedActionType.CUSTOM_TOUCH,
                target = "Game Interaction",
                normX = 500,
                normY = 850,
                confidence = 0.85f,
                reason = "Game environment active. Directing touch gesture to action region."
            )
        }

        // 3. Search and Type Workflow
        if (goalLower.contains("search") || goalLower.contains("ढूंढो") || goalLower.contains("khojo")) {
            val queryToSearch = extractSearchQuery(task.goal)

            // Look for existing editable field
            val editableTarget = currentScreen?.elements?.find { it.isEditable }
            if (editableTarget != null) {
                // If text already matches, submit search or wait
                val currentText = editableTarget.text ?: ""
                if (currentText.contains(queryToSearch, ignoreCase = true)) {
                    return PlannedAction(
                        type = PlannedActionType.TAP,
                        target = "Search Submit / Enter",
                        targetElement = editableTarget,
                        confidence = 0.88f,
                        reason = "Search query already typed. Submitting search."
                    )
                }
                return PlannedAction(
                    type = PlannedActionType.TYPE_TEXT,
                    target = "Search Input",
                    textToType = queryToSearch,
                    targetElement = editableTarget,
                    confidence = 0.95f,
                    reason = "Editable search field detected on screen."
                )
            }

            // Otherwise, look for Search button/icon
            val searchBtn = targetDetector.findTarget(currentScreen, "search")
            if (searchBtn.isConfident && searchBtn.element != null) {
                return PlannedAction(
                    type = PlannedActionType.TAP,
                    target = "Search Button",
                    targetElement = searchBtn.element,
                    confidence = searchBtn.confidence,
                    reason = "Tapping search icon to reveal input field."
                )
            }
        }

        // 4. Specific Target Request (e.g. "Tap Play", "Open Settings")
        val targetQuery = extractTargetName(task.goal)
        if (targetQuery.isNotBlank()) {
            val match = targetDetector.findTarget(currentScreen, targetQuery)
            if (match.isConfident && match.element != null) {
                return PlannedAction(
                    type = PlannedActionType.TAP,
                    target = match.targetLabel,
                    targetElement = match.element,
                    confidence = match.confidence,
                    reason = match.reason
                )
            }
        }

        return null
    }

    private fun extractSearchQuery(goal: String): String {
        val regex = Regex("""(?:search\s+(?:for\s+)?|search\s+karo\s+|khojo\s+)(.+?)(?:\s+in\s+|\s+par\s+|$|\.)""", RegexOption.IGNORE_CASE)
        val match = regex.find(goal)
        return match?.groupValues?.getOrNull(1)?.trim() ?: goal.substringAfter("search", "").trim()
    }

    private fun extractTargetName(goal: String): String {
        val clean = goal.replace(Regex("""(?i)\b(tap|click|open|press|select|dabaao|kholo)\b"""), "").trim()
        return clean
    }
}
