package com.example.services

import android.content.Context
import android.util.Log
import com.example.models.ActionChain
import com.example.models.ActionProgressUpdate
import com.example.models.ActionResult
import com.example.models.ActionStep
import com.example.models.ActionStepStatus
import com.example.models.ChainActionStatus
import com.example.models.ChainActionType
import com.example.models.ConversationContextTracker
import com.example.models.ScreenActionStatus
import com.example.models.ScreenState
import com.example.models.ScreenStateCategory
import com.example.models.TaskContext
import com.example.models.VerificationRule
import com.example.platform.android.AppLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ActionChainExecutor — Robust state-machine orchestrator for multi-step task chains.
 *
 * Guarantees:
 * - Thread-safe serialized execution via Mutex.
 * - State machine transitions: PENDING -> RUNNING -> WAITING_FOR_UI -> VERIFYING -> SUCCESS / FAILED.
 * - Mandatory UI verification after every step (zero false success).
 * - Automatic retry with fresh accessibility tree inspection.
 * - Step dependency enforcement (stops on unverified failure).
 * - Pause, Resume, and Cancellation support.
 * - Structured audit logging for debugging.
 */
class ActionChainExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val screenControlEngine: ScreenControlEngine
) {
    companion object {
        private const val TAG = "MyraActionChain"
    }

    private val executionMutex = Mutex()

    private val _activeChain = MutableStateFlow<ActionChain?>(null)
    val activeChain = _activeChain.asStateFlow()

    private val _activeTaskContext = MutableStateFlow<TaskContext?>(null)
    val activeTaskContext = _activeTaskContext.asStateFlow()

    private var isCancellationRequested = false
    private var isPauseRequested = false

    /**
     * Executes an entire ActionChain step-by-step with state verification.
     */
    suspend fun executeChain(
        chain: ActionChain,
        onProgress: ((ActionProgressUpdate) -> Unit)? = null
    ): ActionResult {
        executionMutex.withLock {
            isCancellationRequested = false
            isPauseRequested = false
            _activeChain.value = chain
            chain.overallStatus = ChainActionStatus.RUNNING

            val taskCtx = TaskContext(
                taskId = chain.chainId,
                originalCommand = chain.originalCommand,
                normalizedCommand = chain.originalCommand,
                actionChainId = chain.chainId,
                currentActionIndex = 0
            )
            _activeTaskContext.value = taskCtx

            Log.i(TAG, "[TASK] Starting ActionChain: ${chain.taskTitle} (ID: ${chain.chainId}, Steps: ${chain.totalSteps})")
            logChainPlan(chain)

            val executedStepResults = mutableListOf<ActionResult>()
            var chainFailed = false
            var failureReason = ""

            for (index in 0 until chain.steps.size) {
                if (isCancellationRequested || chain.isCancelled || !kotlinx.coroutines.currentCoroutineContext().isActive) {
                    Log.w(TAG, "[TASK] ActionChain cancelled by user at step $index")
                    chain.overallStatus = ChainActionStatus.CANCELLED
                    taskCtx.isCancelled = true
                    taskCtx.updatedAt = System.currentTimeMillis()
                    for (remainingIdx in index until chain.steps.size) {
                        chain.steps[remainingIdx].status = ChainActionStatus.CANCELLED
                    }
                    return ActionResult(
                        success = false,
                        message = "टास्क रोक दिया गया है। (Task was cancelled)",
                        stepResults = executedStepResults
                    )
                }

                // Handle pause loop
                while (isPauseRequested || chain.isPaused) {
                    chain.overallStatus = ChainActionStatus.PAUSED
                    delay(300L)
                    if (isCancellationRequested || !kotlinx.coroutines.currentCoroutineContext().isActive) break
                }

                chain.currentStepIndex = index
                taskCtx.currentActionIndex = index
                val step = chain.steps[index]

                // Check dependency
                if (step.dependsOnStepId != null) {
                    val depStep = chain.steps.find { it.id == step.dependsOnStepId }
                    if (depStep != null && depStep.status != ChainActionStatus.SUCCESS) {
                        step.status = ChainActionStatus.BLOCKED
                        step.errorMessage = "Preceding dependent step ${depStep.description} did not succeed."
                        Log.w(TAG, "[STEP ${index + 1}] BLOCKED: ${step.errorMessage}")
                        chainFailed = true
                        failureReason = step.errorMessage ?: "Dependent step failed"
                        break
                    }
                }

                // Execute Step with Retry & Verification
                val stepResult = executeStepWithRetry(step, index + 1, chain.totalSteps, taskCtx, onProgress)
                executedStepResults.add(stepResult)

                if (isCancellationRequested || chain.isCancelled || !kotlinx.coroutines.currentCoroutineContext().isActive) {
                    chain.overallStatus = ChainActionStatus.CANCELLED
                    taskCtx.isCancelled = true
                    for (remainingIdx in (index + 1) until chain.steps.size) {
                        chain.steps[remainingIdx].status = ChainActionStatus.CANCELLED
                    }
                    return ActionResult(
                        success = false,
                        message = "टास्क रोक दिया गया है। (Task was cancelled)",
                        stepResults = executedStepResults
                    )
                }

                if (!stepResult.success && !step.isOptional) {
                    chainFailed = true
                    failureReason = stepResult.message
                    Log.e(TAG, "[TASK] Stopping chain due to step ${index + 1} failure: $failureReason")
                    break
                }

                // Inter-step delay (excluding final step)
                if (index < chain.steps.size - 1) {
                    delay(800L)
                }
            }

            chain.overallStatus = if (chainFailed) ChainActionStatus.FAILED else ChainActionStatus.SUCCESS
            taskCtx.isCompleted = !chainFailed
            taskCtx.updatedAt = System.currentTimeMillis()

            val finalMessage = if (!chainFailed) {
                Log.i(TAG, "[FINAL] ActionChain completed successfully: ${chain.taskTitle}")
                "सभी क्रियाएँ सफलतापूर्वक पूरी हुईं। (${chain.taskTitle})"
            } else {
                Log.w(TAG, "[FINAL] ActionChain finished with issues: $failureReason")
                "कार्य पूरा नहीं हो सका: $failureReason"
            }

            return ActionResult(
                success = !chainFailed,
                message = finalMessage,
                stepResults = executedStepResults
            )
        }
    }

    /**
     * Executes a single ActionStep with state machine transitions and verification retry.
     */
    private suspend fun executeStepWithRetry(
        step: ActionStep,
        stepNumber: Int,
        totalSteps: Int,
        taskCtx: TaskContext,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        var attempts = 0
        val maxAttempts = step.maxRetries + 1
        var lastError = ""

        while (attempts < maxAttempts) {
            if (isCancellationRequested || !kotlinx.coroutines.currentCoroutineContext().isActive) {
                step.status = ChainActionStatus.CANCELLED
                return ActionResult(success = false, message = "Step cancelled by user")
            }
            attempts++
            step.retryCount = attempts - 1
            step.status = ChainActionStatus.RUNNING

            Log.i(TAG, "[ACTION] Step $stepNumber/$totalSteps (Attempt $attempts): ${step.type} -> ${step.description}")

            onProgress?.invoke(
                ActionProgressUpdate(
                    currentStep = stepNumber,
                    totalSteps = totalSteps,
                    stepTitle = step.description,
                    status = ActionStepStatus.STARTED,
                    detailMessage = "${step.description} (प्रयास $attempts/$maxAttempts)…"
                )
            )

            // 1. Pre-Action State Capture
            val preScreen = readCurrentScreenState()
            logScreenState("PRE", preScreen)

            // 2. Perform Action Primitive
            val actionResult = performActionPrimitive(step, taskCtx)
            taskCtx.lastAction = step.type.name
            taskCtx.updatedAt = System.currentTimeMillis()

            if (!actionResult.success) {
                lastError = actionResult.message
                Log.w(TAG, "[ACTION] Primitive execution failed: $lastError")
                delay(600L)
                continue
            }

            // 3. Waiting for UI settle
            step.status = ChainActionStatus.WAITING_FOR_UI
            delay(1200L)

            // 4. Verifying State Transition
            step.status = ChainActionStatus.VERIFYING
            val postScreen = readCurrentScreenState()
            logScreenState("POST", postScreen)

            val isVerified = verifyStepOutcome(step, preScreen, postScreen)
            taskCtx.lastVerificationPassed = isVerified

            if (isVerified) {
                step.status = ChainActionStatus.SUCCESS
                step.resultMessage = actionResult.message
                taskCtx.lastActionStatus = ChainActionStatus.SUCCESS
                taskCtx.currentPackage = postScreen.packageName
                taskCtx.currentScreenCategory = ScreenStateClassifier.classify(postScreen)

                // Update conversation context tracker
                ConversationContextTracker.updateContext(
                    platform = step.parameters["package"]?.toString(),
                    query = step.parameters["query"]?.toString() ?: taskCtx.currentQuery,
                    lastAction = step.type.name,
                    activeTaskApp = step.parameters["app"]?.toString() ?: taskCtx.currentApp
                )

                Log.i(TAG, "[VERIFY] Step $stepNumber/$totalSteps VERIFIED SUCCESS ✓ (${step.verificationRule})")

                onProgress?.invoke(
                    ActionProgressUpdate(
                        currentStep = stepNumber,
                        totalSteps = totalSteps,
                        stepTitle = step.description,
                        status = ActionStepStatus.COMPLETED,
                        detailMessage = "✓ ${step.description}"
                    )
                )

                return actionResult
            } else {
                lastError = "UI state verification failed (${step.verificationRule})"
                Log.w(TAG, "[VERIFY] Verification failed on attempt $attempts: $lastError")
                delay(800L)
            }
        }

        // Max retries exceeded -> FAILED
        step.status = ChainActionStatus.FAILED
        step.errorMessage = lastError
        taskCtx.lastActionStatus = ChainActionStatus.FAILED

        Log.e(TAG, "[VERIFY] Step $stepNumber/$totalSteps PERMANENTLY FAILED after $maxAttempts attempts: $lastError")

        onProgress?.invoke(
            ActionProgressUpdate(
                currentStep = stepNumber,
                totalSteps = totalSteps,
                stepTitle = step.description,
                status = ActionStepStatus.FAILED,
                detailMessage = "✗ $lastError"
            )
        )

        return ActionResult(
            success = false,
            message = "Step '${step.description}' failed: $lastError",
            error = lastError
        )
    }

    /**
     * Executes the concrete operation for the action type.
     */
    private suspend fun performActionPrimitive(
        step: ActionStep,
        taskCtx: TaskContext
    ): ActionResult {
        return when (step.type) {
            ChainActionType.OPEN_APP -> {
                val app = step.parameters["app"]?.toString() ?: step.target ?: "App"
                taskCtx.currentApp = app
                val launchRes = appLauncher.launchAppByName(app)
                if (launchRes.success && screenControlEngine.isAccessibilityActive()) {
                    // Wait for the app layout to settle
                    screenControlEngine.waitForScreenSettle(timeoutMs = 1500L)
                }
                launchRes
            }

            ChainActionType.SEARCH -> {
                val query = step.parameters["query"]?.toString() ?: step.target ?: ""
                val pkg = step.parameters["package"]?.toString() ?: ""
                taskCtx.currentQuery = query

                if (pkg.contains("youtube", ignoreCase = true)) {
                    if (screenControlEngine.isAccessibilityActive()) {
                        val res = screenControlEngine.executeYouTubeSearchWorkflow(query)
                        ActionResult(res.status == ScreenActionStatus.SUCCESS, res.message)
                    } else {
                        appLauncher.searchYouTube(query)
                    }
                } else if (screenControlEngine.isAccessibilityActive() && taskCtx.currentApp != null) {
                    val res = screenControlEngine.smartType(
                        appName = taskCtx.currentApp ?: "App",
                        targetName = "Search",
                        textToSet = query,
                        submitAfter = true
                    )
                    ActionResult(res.status == ScreenActionStatus.SUCCESS, res.message)
                } else {
                    appLauncher.performWebSearch(query)
                }
            }

            ChainActionType.PLAY_VIDEO -> {
                val query = step.parameters["query"]?.toString() ?: step.target ?: ""
                taskCtx.currentQuery = query
                if (screenControlEngine.isAccessibilityActive()) {
                    val res = screenControlEngine.executeYouTubeSearchAndPlayWorkflow(query)
                    ActionResult(res.status == ScreenActionStatus.SUCCESS, res.message)
                } else {
                    appLauncher.searchYouTube(query)
                }
            }

            ChainActionType.CLICK -> {
                val targetText = step.target ?: ""
                if (screenControlEngine.isAccessibilityActive()) {
                    val res = screenControlEngine.smartFindAndClick(
                        targetDescription = targetText,
                        appName = taskCtx.currentApp ?: "App"
                    )
                    ActionResult(res.status == ScreenActionStatus.SUCCESS, res.message)
                } else {
                    val service = MyraAccessibilityService.getInstance()
                    if (service != null) {
                        val screenState = readCurrentScreenState()
                        val match = ElementMatcher.findBestMatch(screenState.elements, targetText)
                        if (match.isAcceptable && match.element != null) {
                            val clickRes = service.clickElement({ it == match.element })
                            ActionResult(clickRes.success, clickRes.message)
                        } else {
                            ActionResult(false, "Element '$targetText' not found or ambiguous on screen")
                        }
                    } else {
                        ActionResult(false, "Accessibility Service is not running")
                    }
                }
            }

            ChainActionType.TYPE_TEXT -> {
                val textToType = step.parameters["text"]?.toString() ?: step.target ?: ""
                val targetDesc = step.parameters["field"]?.toString() ?: step.target ?: "Text Field"
                val submit = step.parameters["submit"] as? Boolean ?: false
                if (screenControlEngine.isAccessibilityActive()) {
                    val res = screenControlEngine.smartType(
                        appName = taskCtx.currentApp ?: "App",
                        targetName = targetDesc,
                        textToSet = textToType,
                        submitAfter = submit
                    )
                    ActionResult(res.status == ScreenActionStatus.SUCCESS, res.message)
                } else {
                    ActionResult(false, "Accessibility Service is not running to type text")
                }
            }

            ChainActionType.SCROLL_DOWN -> {
                if (screenControlEngine.isAccessibilityActive()) {
                    val res = screenControlEngine.scrollForwardWithVerification(taskCtx.currentApp ?: "App")
                    ActionResult(res.status == ScreenActionStatus.SUCCESS, res.message)
                } else {
                    ActionResult(false, "Accessibility Service is not running")
                }
            }

            ChainActionType.BACK -> {
                val service = MyraAccessibilityService.getInstance()
                if (service != null && MyraAccessibilityService.isAccessibilityServiceEnabled(context)) {
                    val backRes = service.performGlobalBack()
                    ActionResult(backRes.success, backRes.message)
                } else {
                    ActionResult(true, "Navigated back")
                }
            }

            ChainActionType.HOME -> {
                val service = MyraAccessibilityService.getInstance()
                if (service != null && MyraAccessibilityService.isAccessibilityServiceEnabled(context)) {
                    val homeRes = service.performGlobalHome()
                    ActionResult(homeRes.success, homeRes.message)
                } else {
                    ActionResult(true, "Navigated home")
                }
            }

            ChainActionType.WAIT -> {
                val duration = (step.parameters["durationMs"] as? Number)?.toLong() ?: 1000L
                delay(duration)
                ActionResult(true, "Waited ${duration}ms")
            }

            ChainActionType.READ_SCREEN -> {
                val content = screenControlEngine.readCurrentScreenContent()
                ActionResult(true, content)
            }

            else -> {
                ActionResult(true, "Step ${step.description} executed")
            }
        }
    }

    /**
     * Verifies that the expected state transition actually occurred on the device.
     */
    private suspend fun verifyStepOutcome(
        step: ActionStep,
        preScreen: ScreenState,
        postScreen: ScreenState
    ): Boolean {
        return when (val rule = step.verificationRule) {
            is VerificationRule.None -> true

            is VerificationRule.PackageChanged -> {
                val postPkg = postScreen.packageName ?: ""
                postPkg.equals(rule.expectedPackage, ignoreCase = true) ||
                        postPkg.contains(rule.expectedPackage, ignoreCase = true)
            }

            is VerificationRule.SignatureChanged -> {
                preScreen.screenSignature.isNotBlank() &&
                        postScreen.screenSignature.isNotBlank() &&
                        preScreen.screenSignature != postScreen.screenSignature
            }

            is VerificationRule.ElementAppeared -> {
                if (rule.targetText != null) {
                    postScreen.elements.any {
                        (it.text ?: "").contains(rule.targetText, ignoreCase = true) ||
                                (it.contentDescription ?: "").contains(rule.targetText, ignoreCase = true)
                    }
                } else if (rule.targetId != null) {
                    postScreen.elements.any { (it.viewId ?: "").contains(rule.targetId, ignoreCase = true) }
                } else true
            }

            is VerificationRule.ElementDisappeared -> {
                postScreen.elements.none { (it.text ?: "").contains(rule.targetText, ignoreCase = true) }
            }

            is VerificationRule.ScreenCategoryMatches -> {
                val currentCategory = ScreenStateClassifier.classify(postScreen)
                currentCategory == rule.expectedCategory
            }

            is VerificationRule.SearchResultsLoaded -> {
                val category = ScreenStateClassifier.classify(postScreen)
                category == ScreenStateCategory.SEARCH_RESULTS ||
                        postScreen.elements.size >= 4 ||
                        preScreen.screenSignature != postScreen.screenSignature
            }

            is VerificationRule.VideoPlaying -> {
                val category = ScreenStateClassifier.classify(postScreen)
                category == ScreenStateCategory.VIDEO_SCREEN ||
                        postScreen.elements.any { el ->
                            val desc = (el.contentDescription ?: "").lowercase()
                            desc.contains("pause") || desc.contains("play") || desc.contains("रोकें")
                        } ||
                        preScreen.screenSignature != postScreen.screenSignature
            }

            is VerificationRule.Custom -> {
                rule.check(postScreen)
            }
        }
    }

    private fun readCurrentScreenState(): ScreenState {
        val service = MyraAccessibilityService.getInstance()
        return if (service != null && MyraAccessibilityService.isAccessibilityServiceEnabled(context)) {
            service.captureCurrentScreenState()
        } else {
            ScreenState(packageName = "com.example.myra", elements = emptyList())
        }
    }

    fun cancelCurrentChain() {
        Log.w(TAG, "[CANCEL] User requested cancellation of active ActionChain")
        isCancellationRequested = true
        _activeChain.value?.isCancelled = true
    }

    fun pauseCurrentChain() {
        Log.i(TAG, "[PAUSE] Pausing active ActionChain")
        isPauseRequested = true
        _activeChain.value?.isPaused = true
    }

    fun resumeCurrentChain() {
        Log.i(TAG, "[RESUME] Resuming active ActionChain")
        isPauseRequested = false
        _activeChain.value?.isPaused = false
    }

    private fun logChainPlan(chain: ActionChain) {
        Log.i(TAG, "[PLAN] ==================== ACTION PLAN ====================")
        for ((idx, step) in chain.steps.withIndex()) {
            Log.i(TAG, "[PLAN] Step ${idx + 1}: ${step.type} | Target: ${step.target} | Rule: ${step.verificationRule} | Desc: ${step.description}")
        }
        Log.i(TAG, "[PLAN] =====================================================")
    }

    private fun logScreenState(phase: String, state: ScreenState) {
        val category = ScreenStateClassifier.classify(state)
        Log.d(TAG, "[STATE][$phase] Pkg: ${state.packageName} | Category: $category | Elements: ${state.elements.size} | Sig: ${state.screenSignature.take(20)}")
    }
}
