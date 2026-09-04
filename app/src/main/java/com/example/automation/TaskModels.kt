package com.example.automation

import com.example.models.ScreenState
import com.example.models.UIElement

enum class TaskState {
    IDLE,
    PLANNING,
    WAITING_FOR_PERMISSION,
    OBSERVING,
    ACTING,
    VERIFYING,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class PlannedActionType {
    TAP,
    TYPE_TEXT,
    SCROLL,
    SWIPE,
    BACK,
    WAIT,
    LONG_PRESS,
    OPEN_APP,
    CUSTOM_TOUCH
}

data class PlannedAction(
    val type: PlannedActionType,
    val target: String,
    val textToType: String? = null,
    val targetElement: UIElement? = null,
    val normX: Int? = null,
    val normY: Int? = null,
    val confidence: Float = 0.90f,
    val reason: String = ""
)

data class StepExecutionRecord(
    val stepIndex: Int,
    val action: PlannedAction,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val verified: Boolean,
    val screenBefore: ScreenState?,
    val screenAfter: ScreenState?,
    val message: String
)

data class AutonomousTask(
    val taskId: String = java.util.UUID.randomUUID().toString(),
    val goal: String,
    var state: TaskState = TaskState.PLANNING,
    val targetApp: String? = null,
    val history: MutableList<StepExecutionRecord> = mutableListOf(),
    var retryCount: Int = 0,
    val maxRetries: Int = 3,
    val maxSteps: Int = 8,
    val timeoutMs: Long = 30_000L,
    val startTime: Long = System.currentTimeMillis(),
    var statusMessage: String = "Task initialized."
) {
    val isRunning: Boolean
        get() = state != TaskState.IDLE &&
                state != TaskState.COMPLETED &&
                state != TaskState.FAILED &&
                state != TaskState.CANCELLED
}
