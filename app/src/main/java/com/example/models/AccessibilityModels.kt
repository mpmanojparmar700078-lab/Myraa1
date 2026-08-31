package com.example.models

import android.graphics.Rect

/**
 * Clean data model representing a single UI element extracted from the Accessibility Node Tree.
 */
data class UIElement(
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val viewId: String? = null,
    val isClickable: Boolean = false,
    val isEnabled: Boolean = true,
    val isFocusable: Boolean = false,
    val isFocused: Boolean = false,
    val isSelected: Boolean = false,
    val isChecked: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isPassword: Boolean = false,
    val isVisibleToUser: Boolean = true,
    val bounds: Rect = Rect(),
    val childCount: Int = 0,
    val parentClassName: String? = null,
    val parentViewId: String? = null,
    val supportedActions: List<Int> = emptyList(),
    val nodeIndex: Int = 0
) {
    /**
     * Convenient display label for logging or debugging.
     */
    val displayLabel: String
        get() = text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: viewId?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
            ?: className?.substringAfterLast(".")
            ?: "Unknown"

    val fullSemanticText: String
        get() = buildString {
            if (!text.isNullOrBlank()) append(text.trim())
            if (!contentDescription.isNullOrBlank()) {
                if (isNotEmpty()) append(" ")
                append(contentDescription.trim())
            }
        }
}

/**
 * Confidence level of an element match evaluation.
 */
enum class MatchConfidenceLevel(val threshold: Float) {
    HIGH(0.85f),
    MEDIUM(0.60f),
    LOW(0.35f),
    NONE(0.0f)
}

/**
 * Match evaluation result with scoring, ambiguity detection, and detailed reasoning.
 */
data class ElementMatchResult(
    val element: UIElement? = null,
    val score: Float = 0.0f,
    val confidenceLevel: MatchConfidenceLevel = MatchConfidenceLevel.NONE,
    val isAmbiguous: Boolean = false,
    val competingMatches: List<UIElement> = emptyList(),
    val matchReason: String = ""
) {
    val isAcceptable: Boolean
        get() = element != null && !isAmbiguous && confidenceLevel >= MatchConfidenceLevel.MEDIUM
}

/**
 * Structured snapshot of the active screen retrieved via Myra Accessibility Service.
 */
data class ScreenState(
    val packageName: String? = null,
    val className: String? = null,
    val windowTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val elements: List<UIElement> = emptyList()
) {
    val clickableElements: List<UIElement>
        get() = elements.filter { it.isClickable }

    val editableElements: List<UIElement>
        get() = elements.filter { it.isEditable }

    val scrollableElements: List<UIElement>
        get() = elements.filter { it.isScrollable }

    val readableLabels: List<String>
        get() = elements.mapNotNull { it.text ?: it.contentDescription }.filter { it.isNotBlank() }.distinct()

    /**
     * Generates a deterministic signature of the current screen to detect transitions and changes.
     */
    val screenSignature: String
        get() {
            val pkg = packageName ?: "none"
            val textSummary = elements.take(15)
                .mapNotNull { it.text ?: it.contentDescription }
                .filter { it.isNotBlank() }
                .joinToString("|")
            val count = elements.size
            return "$pkg#$count#${textSummary.hashCode()}"
        }

    fun hasChangedSignificantly(other: ScreenState?): Boolean {
        if (other == null) return true
        if (this.packageName != other.packageName) return true
        if (kotlin.math.abs(this.elements.size - other.elements.size) > 3) return true
        return this.screenSignature != other.screenSignature
    }

    fun hasText(query: String, ignoreCase: Boolean = true): Boolean {
        val q = query.trim()
        if (q.isBlank()) return false
        return elements.any { el ->
            (el.text?.contains(q, ignoreCase = ignoreCase) == true) ||
            (el.contentDescription?.contains(q, ignoreCase = ignoreCase) == true)
        }
    }
}

/**
 * Basic accessibility actions supported on the device.
 */
enum class AccessibilityActionType {
    CLICK,
    LONG_CLICK,
    SET_TEXT,
    APPEND_TEXT,
    CLEAR_TEXT,
    FOCUS,
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    GESTURE_TAP,
    GESTURE_SCROLL,
    READ_SCREEN
}

/**
 * High-level status for screen interaction workflows.
 */
enum class ScreenActionStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    ELEMENT_NOT_FOUND,
    AMBIGUOUS_ELEMENT,
    VERIFICATION_FAILED,
    STALE_NODE,
    NOT_SUPPORTED,
    ACCESSIBILITY_PERMISSION_REQUIRED,
    APP_NOT_INSTALLED,
    UI_ELEMENT_NOT_AVAILABLE
}

/**
 * Result of an accessibility node action.
 */
data class AccessibilityActionResult(
    val success: Boolean,
    val actionType: AccessibilityActionType,
    val message: String,
    val targetDescription: String? = null
)

/**
 * Detailed step execution result for screen control workflows.
 */
data class ScreenActionStepResult(
    val status: ScreenActionStatus,
    val message: String,
    val currentPackage: String? = null,
    val foundElement: UIElement? = null,
    val detectedResults: List<String> = emptyList(),
    val durationMs: Long = 0L
)

/**
 * Real-time event log for developer inspection mode and smart screen action engine (Part 11).
 */
data class AccessibilityDebugLog(
    val actionId: String = java.util.UUID.randomUUID().toString(),
    val actionType: String = "",
    val status: String = "COMPLETED",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val result: String = "",
    val errorState: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String = "",
    val actionName: String = "",
    val target: String = ""
)

/**
 * YouTube candidate classification types for V3.3 intelligent result control.
 */
enum class VideoCandidateType(val label: String) {
    VIDEO("VIDEO"),
    SHORT("SHORT"),
    AD("AD / SPONSORED"),
    CHANNEL("CHANNEL"),
    PLAYLIST("PLAYLIST"),
    UNKNOWN("UNKNOWN")
}

/**
 * Structured candidate YouTube search result extracted from Accessibility tree (Part 2 & Part 3).
 */
data class VideoCandidate(
    val title: String,
    val element: UIElement,
    val rawText: String,
    val type: VideoCandidateType,
    val relevanceScore: Float,
    val isClickable: Boolean,
    val confidence: Float,
    val rejectionReason: String? = null
) {
    val isSelectable: Boolean
        get() = type == VideoCandidateType.VIDEO && rejectionReason == null && relevanceScore >= 0.40f

    val isShort: Boolean
        get() = type == VideoCandidateType.SHORT

    val isAdvertisement: Boolean
        get() = type == VideoCandidateType.AD

    val isChannel: Boolean
        get() = type == VideoCandidateType.CHANNEL

    val isPlaylist: Boolean
        get() = type == VideoCandidateType.PLAYLIST
}

/**
 * Full inspection details for Developer Mode / Live Debugging of YouTube search and play (Part 14).
 */
data class YouTubeResultSelection(
    val query: String = "",
    val intent: String = "YOUTUBE_SEARCH_AND_PLAY",
    val candidates: List<VideoCandidate> = emptyList(),
    val selected: VideoCandidate? = null,
    val isAmbiguous: Boolean = false,
    val message: String = "",
    val clickResult: String = "",
    val videoVerificationResult: String = "",
    val playbackVerificationResult: String = "",
    val contextUsed: Boolean = false,
    val contextAgeSeconds: Long = 0L,
    val geminiRequests: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

