package com.example.models

import android.graphics.drawable.Drawable

enum class IntentType {
    NEW_ACTION,
    OPEN_APP,
    CLOSE_APP,
    GO_BACK,
    GO_HOME,
    SCROLL,
    SCROLL_UP,
    SCROLL_DOWN,
    SEARCH,
    TYPE_TEXT,
    CLICK,
    CLICK_ELEMENT,
    SELECT,
    PLAY,
    PAUSE,
    STOP,
    OPEN_SETTINGS,
    READ_SCREEN,
    FIND_ELEMENT,
    EXECUTE_SKILL,
    REPEAT_LAST_ACTION,
    CANCEL_REQUEST,
    CANCEL_CURRENT_REQUEST,
    WEB_SEARCH,
    YOUTUBE_SEARCH,
    YOUTUBE_SEARCH_AND_PLAY,
    SEARCH_AND_PLAY,
    OPEN_PAGE,
    RECALL_RECENT_REQUESTS,
    RECALL_RECENT_REQUEST,
    REPORT_LAST_EXECUTION,
    EXPLAIN_LAST_FAILURE,
    RETRY_LAST_REQUEST,
    OPEN_URL,
    SET_PREFERENCE,
    META_INSTRUCTION,
    PREFERENCE_UPDATE,
    LEARN_REQUEST,
    TEST_REQUEST,
    CHALLENGE_REQUEST,
    GENERAL_CONVERSATION,
    CLARIFICATION,
    REPORT_FAILURE,
    CORRECT_PREVIOUS_RESULT,
    WAIT,
    BACK,
    CLEAR_CHAT,
    MULTI_ACTION,
    PUBLIC_API,
    GENERAL_CHAT,
    UNKNOWN
}

enum class RequestState {
    INTENT_RECEIVED,
    PLAN_CREATED,
    ACTION_STARTED,
    ACTION_SUCCEEDED,
    STEP_FAILED,
    REQUEST_PARTIALLY_COMPLETED,
    REQUEST_SUCCEEDED,
    REQUEST_FAILED,
    REQUEST_CANCELLED,
    RECEIVED,
    PARSING,
    PLANNING,
    EXECUTING,
    WAITING_FOR_SCREEN,
    VERIFYING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED,
    NEEDS_CLARIFICATION
}

data class ExecutionStep(
    val stepId: Int,
    val action: String,
    val target: String? = null,
    val status: RequestState = RequestState.ACTION_STARTED,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val verification: Boolean = false,
    val error: String? = null
)

data class RequestResult(
    val requestId: Long,
    val state: RequestState,
    val summary: String,
    val plannedActions: List<String> = emptyList(),
    val completedActions: List<String> = emptyList(),
    val failedActions: List<String> = emptyList(),
    val actionResults: List<ActionResult> = emptyList(),
    val isVerified: Boolean = false,
    val failureReason: String? = null,
    val wasMisinterpreted: Boolean = false,
    val isSearchOnlyStarted: Boolean = false,
    val steps: List<ExecutionStep> = emptyList()
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val launchedTarget: String? = null,
    val error: String? = null,
    val stepResults: List<ActionResult> = emptyList(),
    val isVerified: Boolean = false,
    val partial: Boolean = false,
    val executionSteps: List<ExecutionStep> = emptyList()
)

data class ParsedIntent(
    val type: IntentType,
    val app: String? = null,
    val target: String? = null,
    val query: String? = null,
    val targetType: String? = null,
    val action: String? = null,
    val key: String? = null,
    val value: String? = null,
    val durationMs: Long? = null,
    val actions: List<ParsedIntent> = emptyList(),
    val subIntents: List<ParsedIntent> = emptyList(),
    val responseText: String? = null,
    val apiId: String? = null,
    val apiParams: Map<String, String> = emptyMap(),
    val confidence: Float = 1.0f,
    val skillName: String? = null,
    val metaInstruction: String? = null,
    val targetIndex: Int? = null,
    val referenceType: String? = null,
    val userCorrection: String? = null,
    val executionPreference: String? = null
)

enum class AssistantState(
    val label: String,
    val descriptionHindi: String,
    val descriptionEnglish: String
) {
    IDLE("Idle", "तैयार (Ready)", "Ready for commands"),
    PROCESSING("Processing", "सोच रही हूँ…", "Analyzing intent with AI…"),
    EXECUTING_ACTION("Executing Action", "एक्शन चला रही हूँ…", "Executing Android action…"),
    LEARNING("Learning", "नया पैटर्न सीख रही हूँ…", "Learning from experience…"),
    BACKGROUND_READY("Background Active", "बैकग्राउंड में सक्रिय", "Active in background"),
    CANCELLING("Cancelling", "रद्द हो रहा है…", "Cancelling active task…"),
    CANCELLED("Cancelled", "रद्द किया गया", "Request cancelled"),
    ERROR("Error", "त्रुटि (Error)", "Action or connection issue")
}

data class LocalCommandResult(
    val handled: Boolean,
    val intent: ParsedIntent? = null,
    val actionResult: ActionResult? = null,
    val responseText: String? = null,
    val confidence: Float = 1.0f,
    val reason: String? = null
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
)

// --- SELF-LEARNING BRAIN & ACTION CHAIN MODELS ---

enum class DecisionSource {
    LOCAL_PARSER,
    LEARNED_SKILL,
    EXPERIENCE_MEMORY,
    SCREEN_CONTEXT,
    GEMINI_FALLBACK
}

data class ExecutionDecision(
    val source: DecisionSource,
    val intent: ParsedIntent,
    val confidence: Float,
    val explanation: String,
    val matchedSkillId: Long? = null,
    val matchedExperienceId: Long? = null
)

data class ActionStep(
    val stepId: Int,
    val actionType: String,
    val target: String? = null,
    val value: String? = null,
    val waitMs: Long = 500L,
    val optional: Boolean = false
)

data class ActionChain(
    val chainId: String,
    val requestId: Long,
    val steps: List<ActionStep>,
    val description: String
)

data class ActionStepResult(
    val stepId: Int,
    val actionType: String,
    val success: Boolean,
    val durationMs: Long = 0L,
    val error: String? = null,
    val verifiedState: String? = null
)

data class ActionChainResult(
    val chainId: String,
    val requestId: Long,
    val success: Boolean,
    val stepResults: List<ActionStepResult>,
    val totalDurationMs: Long,
    val failureReason: String? = null
)

data class ScreenNodeInfo(
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isVisibleToUser: Boolean = true,
    val boundsRect: String? = null
)

data class ScreenSnapshot(
    val packageName: String? = null,
    val activityName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val visibleNodes: List<ScreenNodeInfo> = emptyList()
)

data class DiagnosticLog(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String
)
