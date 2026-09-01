package com.example.models

enum class AssistantState(val label: String, val descriptionHindi: String, val descriptionEnglish: String) {
    IDLE("Idle", "तैयार (Ready)", "Ready for commands"),
    PROCESSING("Processing", "सोच रही हूँ…", "Analyzing intent with AI…"),
    EXECUTING_ACTION("Executing Action", "एक्शन चला रही हूँ…", "Executing Android action…"),
    BACKGROUND_READY("Background Active", "बैकग्राउंड में सक्रिय", "Active in background"),
    CANCELLING("Cancelling", "रद्द हो रहा है…", "Cancelling active task…"),
    CANCELLED("Cancelled", "रद्द किया गया", "Request cancelled"),
    ERROR("Error", "त्रुटि (Error)", "Action or connection issue")
}

enum class IntentType {
    OPEN_APP,
    OPEN_SETTINGS,
    WEB_SEARCH,
    YOUTUBE_SEARCH,
    YOUTUBE_SEARCH_AND_PLAY,
    OPEN_URL,
    SET_PREFERENCE,
    WAIT,
    BACK,
    CLEAR_CHAT,
    MULTI_ACTION,
    GENERAL_CHAT,
    UNKNOWN
}

data class LocalCommandResult(
    val recognized: Boolean,
    val intent: IntentType? = null,
    val parsedIntent: ParsedIntent? = null,
    val parameters: Map<String, Any> = emptyMap(),
    val confidence: Float = 0.0f,
    val reason: String = ""
)

enum class ActionStepStatus {
    PENDING,
    STARTED,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class ActionProgressUpdate(
    val currentStep: Int,
    val totalSteps: Int,
    val stepTitle: String,
    val status: ActionStepStatus,
    val detailMessage: String
)

data class ParsedIntent(
    val type: IntentType,
    val app: String? = null,
    val target: String? = null,
    val query: String? = null,
    val key: String? = null,
    val value: String? = null,
    val durationMs: Long? = null,
    val actions: List<ParsedIntent> = emptyList(),
    val responseText: String? = null,
    val confidence: Float = 1.0f
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val launchedTarget: String? = null,
    val error: String? = null,
    val stepResults: List<ActionResult> = emptyList()
)
