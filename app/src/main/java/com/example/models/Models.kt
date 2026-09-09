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
    GREETING,
    QUESTION,
    NEW_COMMAND,
    NAVIGATE,
    ACTION_CHAIN,
    CONTEXT_QUERY,
    RECALL_REQUEST,
    EXECUTION_STATUS_QUERY,
    FAILURE_FEEDBACK,
    CORRECTION,
    RETRY_REQUEST,
    EXECUTION_PREFERENCE,
    MEMORY_QUERY,
    CONFIRMATION,
    DENIAL,
    API_KEY_STATUS_QUERY,
    WHY_QUERY,
    META_CONVERSATION,
    IDENTITY_QUESTION,
    ASSISTANT_RECALL_QUERY,
    UNKNOWN
}

enum class MessageCategory {
    GREETING,
    GENERAL_CONVERSATION,
    IDENTITY_QUESTION,
    QUESTION,
    API_KEY_STATUS_QUERY,
    WHY_QUESTION,
    META_CONVERSATION,
    NEW_COMMAND,
    FOLLOW_UP,
    CONTEXT_QUESTION,
    EXECUTION_STATUS,
    FAILURE_FEEDBACK,
    RETRY_REQUEST,
    CANCEL_REQUEST,
    CONFIRMATION,
    DENIAL,
    CORRECTION,
    META_INSTRUCTION,
    PREFERENCE,
    MEMORY_QUERY,
    ASSISTANT_RECALL_QUERY,
    UNKNOWN
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class ApiKeyStatus {
    CONFIGURED,
    NOT_CONFIGURED,
    INVALID,
    UNKNOWN
}

data class ConversationMessage(
    val messageId: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val role: MessageRole,
    val rawText: String,
    var normalizedText: String,
    var intent: IntentType = IntentType.UNKNOWN,
    var response: String? = null,
    var relatedRequestId: Long? = null
)

enum class ResponseType {
    TEXT,
    ACTION_PROGRESS,
    ACTION_SUCCESS,
    ACTION_FAILURE,
    PARTIAL_SUCCESS,
    CLARIFICATION,
    CANCELLATION,
    CONVERSATION,
    CONTEXT_REPORT,
    EXECUTION_REPORT,
    API_KEY_REPORT,
    ERROR
}

enum class ExecutionStatus {
    NOT_STARTED,
    PLANNED,
    RUNNING,
    IN_PROGRESS,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED,
    NOT_EXECUTED
}

enum class ContextReferenceType {
    REQUEST_INDEX,
    LAST_REQUEST,
    LAST_FAILURE,
    RELATIVE
}

data class ContextReference(
    val type: ContextReferenceType,
    val index: Int? = null,
    val rawRef: String? = null
)

data class ParsedCommand(
    val rawText: String,
    val normalizedText: String,
    val intent: IntentType,
    val targetApp: String? = null,
    val target: String? = null,
    val query: String? = null,
    val action: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val contextReference: ContextReference? = null,
    val confidence: Float = 1.0f,
    val preference: String? = null,
    val subCommands: List<ParsedCommand> = emptyList(),
    val category: MessageCategory = MessageCategory.UNKNOWN
)

data class RequestRecord(
    val requestId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val rawUserMessage: String,
    val normalizedMessage: String,
    val parsedCommand: ParsedCommand,
    var executionStatus: ExecutionStatus = ExecutionStatus.NOT_STARTED,
    val executionSteps: MutableList<ExecutionStep> = mutableListOf(),
    var verificationResult: Boolean = false,
    var finalResult: String? = null
)

enum class PendingConfirmationType {
    RETRY_PREVIOUS,
    NEED_MORE_HELP,
    CUSTOM
}

data class PendingConfirmation(
    val type: PendingConfirmationType,
    val relatedRequestId: Long? = null,
    val prompt: String = ""
)

data class DetailedExecutionResult(
    val requestId: Long,
    val userCommand: String,
    val intent: ParsedIntent,
    val target: String? = null,
    val currentState: RequestState = RequestState.SUCCESS,
    val steps: List<ExecutionStep> = emptyList(),
    val successfulSteps: List<String> = emptyList(),
    val failedSteps: List<String> = emptyList(),
    val verificationStatus: Boolean = false,
    val finalStatus: ExecutionStatus = ExecutionStatus.SUCCESS,
    val failureReason: String? = null,
    val summary: String? = null,
    val wasMisinterpreted: Boolean = false,
    val isSearchOnlyStarted: Boolean = false,
    val metaInstruction: String? = null,
    val itemIndex: Int? = null,
    val subResults: List<DetailedExecutionResult> = emptyList()
)

data class GeneratedResponse(
    val text: String,
    val responseType: ResponseType,
    val requestId: Long,
    val pendingConfirmation: PendingConfirmation? = null,
    val executionStatus: ExecutionStatus? = null
)

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
    val executionPreference: String? = null,
    val category: MessageCategory = MessageCategory.UNKNOWN
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
    val reason: String? = null,
    val category: MessageCategory = MessageCategory.UNKNOWN
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
