package com.example.models

import java.util.UUID

/**
 * Types of actions supported in the Action Chain State Machine.
 */
enum class ChainActionType {
    OPEN_APP,
    CLICK,
    LONG_CLICK,
    TYPE_TEXT,
    CLEAR_TEXT,
    SEARCH,
    SCROLL_DOWN,
    SCROLL_UP,
    BACK,
    HOME,
    WAIT,
    READ_SCREEN,
    SELECT_RESULT,
    PLAY_VIDEO,
    VERIFY,
    ASK_USER,
    CUSTOM
}

/**
 * Lifecycle states of an ActionStep and an ActionChain.
 */
enum class ChainActionStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_UI,
    VERIFYING,
    SUCCESS,
    FAILED,
    SKIPPED,
    CANCELLED,
    PAUSED,
    WAITING_FOR_USER,
    BLOCKED
}

/**
 * Categories of UI Screen State inferred by the ScreenStateClassifier.
 */
enum class ScreenStateCategory {
    UNKNOWN,
    APP_OPENING,
    HOME_SCREEN,
    SEARCH_SCREEN,
    SEARCHING,
    SEARCH_RESULTS,
    DETAIL_SCREEN,
    VIDEO_SCREEN,
    DIALOG,
    MENU,
    LOADING
}

/**
 * Rules used to verify whether an ActionStep succeeded on screen.
 */
sealed class VerificationRule {
    object None : VerificationRule()
    data class PackageChanged(val expectedPackage: String) : VerificationRule()
    data class ElementAppeared(val targetText: String? = null, val targetId: String? = null) : VerificationRule()
    data class ElementDisappeared(val targetText: String) : VerificationRule()
    data class ScreenCategoryMatches(val expectedCategory: ScreenStateCategory) : VerificationRule()
    object SignatureChanged : VerificationRule()
    object VideoPlaying : VerificationRule()
    object SearchResultsLoaded : VerificationRule()
    data class Custom(val description: String, val check: suspend (ScreenState) -> Boolean) : VerificationRule()
}

/**
 * Candidate item (e.g. search result, video card, menu item) on screen.
 */
data class CandidateItem(
    val index: Int,
    val title: String,
    val subtitle: String? = null,
    val type: String = "ITEM",
    val element: UIElement? = null,
    val confidence: Float = 1.0f,
    val boundsDescription: String? = null,
    val isClickable: Boolean = true
)

/**
 * Confidence level for contextual pronoun and reference resolution.
 */
enum class ReferenceConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

/**
 * Result of resolving a pronoun or positional reference.
 */
data class ResolvedReference(
    val targetEntity: String? = null,
    val targetQuery: String? = null,
    val targetApp: String? = null,
    val targetIndex: Int? = null,
    val confidence: ReferenceConfidence = ReferenceConfidence.UNKNOWN,
    val disambiguationPrompt: String? = null,
    val matchedCandidate: CandidateItem? = null,
    val reason: String = ""
)

/**
 * Atomic step in an ActionChain.
 */
data class ActionStep(
    val id: String = UUID.randomUUID().toString(),
    val type: ChainActionType,
    val target: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val expectedPackage: String? = null,
    val expectedScreenCategory: ScreenStateCategory? = null,
    var status: ChainActionStatus = ChainActionStatus.PENDING,
    var retryCount: Int = 0,
    val maxRetries: Int = 2,
    val timeoutMs: Long = 6000L,
    val verificationRule: VerificationRule = VerificationRule.None,
    var resultMessage: String? = null,
    var errorMessage: String? = null,
    val dependsOnStepId: String? = null,
    val isOptional: Boolean = false,
    val description: String = ""
)

/**
 * Structured sequence of executable actions representing a multi-step task.
 */
data class ActionChain(
    val chainId: String = UUID.randomUUID().toString(),
    val taskTitle: String,
    val originalCommand: String,
    val steps: MutableList<ActionStep> = mutableListOf(),
    var currentStepIndex: Int = 0,
    var overallStatus: ChainActionStatus = ChainActionStatus.PENDING,
    var isCancelled: Boolean = false,
    var isPaused: Boolean = false,
    var contextSnapshot: TaskContext? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalSteps: Int get() = steps.size
    val currentStep: ActionStep? get() = steps.getOrNull(currentStepIndex)
    val isFinished: Boolean
        get() = overallStatus == ChainActionStatus.SUCCESS ||
                overallStatus == ChainActionStatus.FAILED ||
                overallStatus == ChainActionStatus.CANCELLED
}

/**
 * Short-term transient task context tracking current execution progress and screen entities.
 */
data class TaskContext(
    val taskId: String = UUID.randomUUID().toString(),
    val originalCommand: String = "",
    val normalizedCommand: String = "",
    val actionChainId: String? = null,
    var currentActionIndex: Int = 0,
    var currentApp: String? = null,
    var currentPackage: String? = null,
    var currentScreenCategory: ScreenStateCategory = ScreenStateCategory.UNKNOWN,
    var currentEntity: String? = null,
    var currentQuery: String? = null,
    var selectedResult: CandidateItem? = null,
    var candidates: List<CandidateItem> = emptyList(),
    var lastAction: String? = null,
    var lastActionStatus: ChainActionStatus? = null,
    var lastVerificationPassed: Boolean = false,
    var retryCount: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var isCompleted: Boolean = false,
    var isCancelled: Boolean = false
) {
    fun isExpired(timeoutMs: Long = 10 * 60 * 1000L): Boolean {
        return System.currentTimeMillis() - updatedAt > timeoutMs
    }
}
