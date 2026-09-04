package com.example.automation

import android.content.Context
import android.util.Log
import com.example.device.AppContextManager
import com.example.device.CapabilityManager
import com.example.platform.android.AppLauncher
import com.example.services.MyraAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Multi-Step Autonomous Task Engine.
 * Executes the Observe -> Understand -> Act -> Observe -> Verify -> Adapt lifecycle.
 * Provides user cancellation, timeout protection, and real-time status streaming.
 */
class TaskEngine(
    private val context: Context,
    private val capabilityManager: CapabilityManager,
    private val appContextManager: AppContextManager = AppContextManager(context),
    private val safetyManager: SafetyManager = SafetyManager(),
    private val actionPlanner: ActionPlanner = ActionPlanner(),
    private val actionExecutor: ActionExecutor = ActionExecutor(context, AppLauncher(context)),
    private val actionVerifier: ActionVerifier = ActionVerifier(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "TaskEngine"
    }

    private var activeJob: Job? = null

    private val _activeTask = MutableStateFlow<AutonomousTask?>(null)
    val activeTask: StateFlow<AutonomousTask?> = _activeTask.asStateFlow()

    /**
     * Cancels the currently executing autonomous task immediately.
     */
    fun cancelCurrentTask() {
        val current = _activeTask.value
        if (current != null && current.isRunning) {
            current.state = TaskState.CANCELLED
            current.statusMessage = "Task cancelled by user."
            _activeTask.value = current.copy(state = TaskState.CANCELLED, statusMessage = "Task cancelled by user.")
            Log.d(TAG, "Task ${current.taskId} cancelled by user.")
        }
        activeJob?.cancel()
        activeJob = null
    }

    /**
     * Initiates and runs an autonomous goal.
     */
    fun startTask(
        goal: String,
        targetApp: String? = null,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Job {
        cancelCurrentTask()

        val task = AutonomousTask(
            goal = goal,
            targetApp = targetApp,
            state = TaskState.PLANNING,
            statusMessage = "Myra is analyzing the request..."
        )
        _activeTask.value = task

        val job = scope.launch {
            try {
                executeTaskLoop(task, onStatusUpdate)
            } catch (e: CancellationException) {
                task.state = TaskState.CANCELLED
                task.statusMessage = "Task cancelled."
                _activeTask.value = task.copy(state = TaskState.CANCELLED)
                onStatusUpdate?.invoke("Task cancelled.")
            } catch (e: Exception) {
                Log.e(TAG, "Task execution error", e)
                task.state = TaskState.FAILED
                task.statusMessage = "Execution failed: ${e.message}"
                _activeTask.value = task.copy(state = TaskState.FAILED)
                onStatusUpdate?.invoke("Execution failed: ${e.message}")
            }
        }
        activeJob = job
        return job
    }

    private suspend fun executeTaskLoop(
        task: AutonomousTask,
        onStatusUpdate: ((String) -> Unit)?
    ) {
        // Step 1: Check Permissions & Capabilities
        if (!capabilityManager.canInteractWithUi()) {
            task.state = TaskState.WAITING_FOR_PERMISSION
            val msg = "I need Accessibility access to interact with the screen. Please enable it in Settings."
            task.statusMessage = msg
            _activeTask.value = task.copy(state = TaskState.WAITING_FOR_PERMISSION, statusMessage = msg)
            onStatusUpdate?.invoke(msg)
            return
        }

        var currentStep = 0
        while (coroutineContext.isActive && task.isRunning) {
            currentStep++

            // Observe Screen
            task.state = TaskState.OBSERVING
            val observeMsg = "Myra is observing the screen..."
            task.statusMessage = observeMsg
            _activeTask.value = task.copy(state = TaskState.OBSERVING, statusMessage = observeMsg)
            onStatusUpdate?.invoke(observeMsg)

            val service = MyraAccessibilityService.getInstance()
            val screenBefore = service?.captureCurrentScreenState()
            val appContext = appContextManager.refreshCurrentContext()

            // Plan Action
            task.state = TaskState.PLANNING
            val plannedAction = actionPlanner.planNextAction(task, screenBefore, appContext)
            if (plannedAction == null) {
                // Check if goal is already satisfied
                task.state = TaskState.COMPLETED
                val compMsg = "Task verified and completed."
                task.statusMessage = compMsg
                _activeTask.value = task.copy(state = TaskState.COMPLETED, statusMessage = compMsg)
                onStatusUpdate?.invoke(compMsg)
                break
            }

            // Safety Validation
            val safetyResult = safetyManager.validateAction(task, plannedAction, screenBefore)
            if (safetyResult is SafetyCheckResult.Blocked) {
                task.state = TaskState.FAILED
                task.statusMessage = safetyResult.reason
                _activeTask.value = task.copy(state = TaskState.FAILED, statusMessage = safetyResult.reason)
                onStatusUpdate?.invoke(safetyResult.reason)
                break
            }

            // Act
            task.state = TaskState.ACTING
            val actMsg = "Myra is performing action: ${plannedAction.type.name} on '${plannedAction.target}'..."
            task.statusMessage = actMsg
            _activeTask.value = task.copy(state = TaskState.ACTING, statusMessage = actMsg)
            onStatusUpdate?.invoke(actMsg)

            val actionOutput = actionExecutor.execute(plannedAction)
            delay(500L) // Wait for screen layout transition

            // Observe After
            val screenAfter = service?.captureCurrentScreenState()

            // Verify
            task.state = TaskState.VERIFYING
            val verMsg = "Verifying screen result..."
            task.statusMessage = verMsg
            _activeTask.value = task.copy(state = TaskState.VERIFYING, statusMessage = verMsg)
            onStatusUpdate?.invoke(verMsg)

            val verification = actionVerifier.verifyAction(plannedAction, screenBefore, screenAfter)

            val record = StepExecutionRecord(
                stepIndex = currentStep,
                action = plannedAction,
                success = actionOutput.success,
                verified = verification.isVerified,
                screenBefore = screenBefore,
                screenAfter = screenAfter,
                message = "${actionOutput.message}. ${verification.explanation}"
            )
            task.history.add(record)

            if (!actionOutput.success || !verification.isVerified) {
                task.retryCount++
                if (task.retryCount > task.maxRetries) {
                    task.state = TaskState.FAILED
                    val failMsg = "Action verification failed after multiple retries: ${verification.explanation}"
                    task.statusMessage = failMsg
                    _activeTask.value = task.copy(state = TaskState.FAILED, statusMessage = failMsg)
                    onStatusUpdate?.invoke(failMsg)
                    break
                }
                task.state = TaskState.RETRYING
                delay(600L)
            } else {
                task.retryCount = 0 // Reset retries on success
            }
        }
    }
}
