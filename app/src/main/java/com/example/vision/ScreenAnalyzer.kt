package com.example.vision

import com.example.models.ScreenState
import com.example.models.UIElement

data class VisualScreenSummary(
    val packageName: String?,
    val windowTitle: String?,
    val visibleButtons: List<String>,
    val editableFields: List<String>,
    val prominentHeadings: List<String>,
    val totalElementsCount: Int,
    val hasSensitiveFields: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Screen Analyzer for semantic visual decomposition.
 * Extracts accessible landmarks, action targets, and enforces strict privacy
 * by filtering out sensitive/password input fields.
 */
class ScreenAnalyzer {

    /**
     * Synthesizes an actionable summary of the current screen.
     */
    fun analyzeScreenState(screenState: ScreenState?): VisualScreenSummary {
        if (screenState == null || screenState.elements.isEmpty()) {
            return VisualScreenSummary(
                packageName = screenState?.packageName,
                windowTitle = screenState?.windowTitle,
                visibleButtons = emptyList(),
                editableFields = emptyList(),
                prominentHeadings = emptyList(),
                totalElementsCount = 0,
                hasSensitiveFields = false
            )
        }

        val hasPassword = screenState.elements.any { it.isPassword }

        // Sanitize elements: strictly exclude password nodes from semantic logs
        val safeElements = screenState.elements.filter { !it.isPassword }

        val buttons = safeElements
            .filter { it.isClickable && !it.isEditable }
            .mapNotNull { it.text ?: it.contentDescription }
            .filter { it.isNotBlank() }
            .distinct()
            .take(15)

        val inputs = safeElements
            .filter { it.isEditable }
            .mapNotNull { it.text ?: it.contentDescription ?: it.viewId }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)

        val headings = safeElements
            .filter { !it.isClickable && !it.isEditable }
            .mapNotNull { it.text }
            .filter { it.length in 3..40 }
            .distinct()
            .take(8)

        return VisualScreenSummary(
            packageName = screenState.packageName,
            windowTitle = screenState.windowTitle,
            visibleButtons = buttons,
            editableFields = inputs,
            prominentHeadings = headings,
            totalElementsCount = safeElements.size,
            hasSensitiveFields = hasPassword
        )
    }

    /**
     * Formats screen summary into a concise prompt string suitable for Gemini reasoning.
     */
    fun formatForGeminiReasoning(summary: VisualScreenSummary, userGoal: String): String {
        return buildString {
            append("Current Foreground App: ${summary.packageName ?: "Unknown"}\n")
            if (!summary.windowTitle.isNullOrBlank()) {
                append("Window Title: ${summary.windowTitle}\n")
            }
            append("Goal: $userGoal\n")
            if (summary.visibleButtons.isNotEmpty()) {
                append("Clickable UI targets: ${summary.visibleButtons.joinToString(", ")}\n")
            }
            if (summary.editableFields.isNotEmpty()) {
                append("Input fields: ${summary.editableFields.joinToString(", ")}\n")
            }
            if (summary.prominentHeadings.isNotEmpty()) {
                append("Visible text headings: ${summary.prominentHeadings.joinToString(" | ")}\n")
            }
            if (summary.hasSensitiveFields) {
                append("[Notice: Sensitive/Password input field present on screen - data entry is restricted]\n")
            }
        }
    }
}
