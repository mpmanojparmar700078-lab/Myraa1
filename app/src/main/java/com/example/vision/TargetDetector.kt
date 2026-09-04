package com.example.vision

import com.example.models.ScreenState
import com.example.models.UIElement
import java.util.Locale

data class DetectedTarget(
    val element: UIElement?,
    val targetLabel: String,
    val confidence: Float,
    val isAmbiguous: Boolean,
    val reason: String
) {
    val isConfident: Boolean
        get() = element != null && !isAmbiguous && confidence >= 0.65f
}

/**
 * Visual Target Detector evaluating candidate UI elements against target intent.
 * Evaluates text, content description, view IDs, and positions to prevent blind clicks.
 */
class TargetDetector {

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.65f
    }

    /**
     * Finds the best matching element on screen for a target query.
     */
    fun findTarget(
        screenState: ScreenState?,
        targetQuery: String,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
    ): DetectedTarget {
        if (screenState == null || screenState.elements.isEmpty()) {
            return DetectedTarget(
                element = null,
                targetLabel = targetQuery,
                confidence = 0f,
                isAmbiguous = false,
                reason = "Screen contains no visible elements"
            )
        }

        val cleanQuery = targetQuery.trim().lowercase(Locale.ROOT)
        var bestElement: UIElement? = null
        var bestScore = 0.0f
        var bestReason = "No matching element found"
        val highMatches = mutableListOf<UIElement>()

        for (el in screenState.elements) {
            if (el.isPassword) continue // Privacy restriction

            val text = el.text?.lowercase(Locale.ROOT)
            val desc = el.contentDescription?.lowercase(Locale.ROOT)
            val viewId = el.viewId?.substringAfterLast("/")?.lowercase(Locale.ROOT)

            var score = 0.0f
            var reason = ""

            // Exact match
            if (text == cleanQuery || desc == cleanQuery) {
                score = 0.98f
                reason = "Exact match on text/contentDescription"
            } else if (text != null && text.contains(cleanQuery)) {
                val ratio = cleanQuery.length.toFloat() / text.length.coerceAtLeast(1)
                score = 0.75f + (ratio * 0.15f)
                reason = "Partial match in text"
            } else if (desc != null && desc.contains(cleanQuery)) {
                val ratio = cleanQuery.length.toFloat() / desc.length.coerceAtLeast(1)
                score = 0.75f + (ratio * 0.15f)
                reason = "Partial match in contentDescription"
            } else if (viewId != null && viewId.contains(cleanQuery)) {
                score = 0.68f
                reason = "Identifier match in viewId"
            }

            // Boost score for clickable elements
            if (el.isClickable && score > 0f) {
                score = (score + 0.05f).coerceAtMost(1.0f)
            }

            if (score >= confidenceThreshold) {
                highMatches.add(el)
            }

            if (score > bestScore) {
                bestScore = score
                bestElement = el
                bestReason = reason
            }
        }

        val isAmbiguous = highMatches.size > 1 && bestScore < 0.95f

        return DetectedTarget(
            element = if (isAmbiguous) null else bestElement,
            targetLabel = targetQuery,
            confidence = bestScore,
            isAmbiguous = isAmbiguous,
            reason = if (isAmbiguous) "Multiple elements match '$targetQuery' with similar confidence" else bestReason
        )
    }
}
