package com.example.services

import android.content.Context
import android.util.Log
import com.example.actions.ActionManager
import com.example.data.MemoryRepository
import com.example.models.ActionProgressUpdate
import com.example.models.ActionResult
import com.example.models.AssistantState
import com.example.models.ConversationContextTracker
import com.example.models.IntentType
import com.example.models.LocalCommandResult
import com.example.models.ParsedIntent
import com.example.platform.android.AppLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Internal Telemetry & Usage Tracker for Local-First vs Gemini routing.
 * Ensures zero unnecessary Gemini API calls are made and tracks routing levels.
 */
object ApiUsageTracker {
    private const val TAG = "MyraLocalFirst"
    private val localExecutionCounter = AtomicInteger(0)
    private val geminiExecutionCounter = AtomicInteger(0)
    private var lastRoutingDecision = "NONE"

    fun recordLocalExecution(level: String, intent: IntentType?, queryOrApp: String?) {
        val count = localExecutionCounter.incrementAndGet()
        lastRoutingDecision = level
        Log.i(TAG, "[LOCAL-FIRST] Routed locally via $level -> Intent: $intent ($queryOrApp) | LocalTotal: $count, GeminiTotal: ${geminiExecutionCounter.get()}")
    }

    fun recordGeminiExecution(reason: String) {
        val count = geminiExecutionCounter.incrementAndGet()
        lastRoutingDecision = "LEVEL_5_GEMINI_FALLBACK"
        Log.i(TAG, "[GEMINI-API] Falling back to Gemini API (Reason: $reason) | LocalTotal: ${localExecutionCounter.get()}, GeminiTotal: $count")
    }

    val localCount: Int get() = localExecutionCounter.get()
    val geminiCount: Int get() = geminiExecutionCounter.get()
    val lastDecision: String get() = lastRoutingDecision

    fun resetForTesting() {
        localExecutionCounter.set(0)
        geminiExecutionCounter.set(0)
        lastRoutingDecision = "NONE"
    }
}

/**
 * Core Assistant Coordinator implementing strict Local-First Architecture:
 * LEVEL 1 — Deterministic Local Command (Instant execution, 0 Gemini requests)
 * LEVEL 2 — Context Resolution (Pronoun & follow-up resolution via ConversationContextTracker)
 * LEVEL 3 — Android / Accessibility / Device API (App launcher, system navigation, screen control)
 * LEVEL 4 — Local Search / Intent Processing (YouTube search, Google search, URL opening)
 * LEVEL 5 — Gemini AI Engine (Fallback ONLY for complex reasoning & creative dialog)
 */
class AssistantService(
    private val context: Context,
    val memoryRepository: MemoryRepository
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val appLauncher = AppLauncher(context)
    val screenControlEngine = ScreenControlEngine(context, appLauncher)
    val actionManager = ActionManager(appLauncher, memoryRepository, screenControlEngine)
    val localCommandParser = LocalCommandParser()
    val commandParser = CommandParser(localCommandParser)
    val geminiService = GeminiService(commandParser)
    val actionChainPlanner = ActionChainPlanner(localCommandParser)
    val actionChainExecutor = ActionChainExecutor(context, appLauncher, screenControlEngine)

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState = _assistantState.asStateFlow()

    private val _lastActionResult = MutableStateFlow<ActionResult?>(null)
    val lastActionResult = _lastActionResult.asStateFlow()

    private val _currentActionProgress = MutableStateFlow<ActionProgressUpdate?>(null)
    val currentActionProgress = _currentActionProgress.asStateFlow()

    fun processCommand(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        // Prevent accidental duplicate submission while already busy
        if (_assistantState.value == AssistantState.PROCESSING || _assistantState.value == AssistantState.EXECUTING_ACTION) {
            return
        }

        serviceScope.launch {
            try {
                // 1. Save user message to memory repository
                memoryRepository.saveUserMessage(trimmed)
                _assistantState.value = AssistantState.PROCESSING
                _currentActionProgress.value = null

                // ====================================================================
                // STRICT LOCAL-FIRST ROUTING HIERARCHY (0 GEMINI API CALLS FOR LOCAL)
                // ====================================================================

                // STEP 1: Full Local Engine Evaluation (Deterministic, Context, Device, Search, Multi-Action)
                val localResult = localCommandParser.parse(trimmed)
                if (localResult.recognized && localResult.parsedIntent != null) {
                    val routingLevel = when (localResult.intent) {
                        IntentType.CLEAR_CHAT, IntentType.SET_PREFERENCE -> "LEVEL_1_DETERMINISTIC"
                        IntentType.GENERAL_CHAT -> if (localResult.reason.contains("context", ignoreCase = true)) "LEVEL_2_CONTEXT" else "LEVEL_1_DETERMINISTIC"
                        IntentType.BACK -> "LEVEL_3_DEVICE_NAVIGATION"
                        IntentType.OPEN_APP, IntentType.OPEN_SETTINGS -> "LEVEL_3_ANDROID_API"
                        IntentType.WEB_SEARCH, IntentType.YOUTUBE_SEARCH, IntentType.YOUTUBE_SEARCH_AND_PLAY, IntentType.OPEN_URL -> "LEVEL_4_LOCAL_SEARCH"
                        IntentType.MULTI_ACTION -> "LEVEL_1_MULTI_ACTION"
                        else -> "LEVEL_1_LOCAL"
                    }
                    ApiUsageTracker.recordLocalExecution(
                        level = routingLevel,
                        intent = localResult.intent,
                        queryOrApp = localResult.parsedIntent.app ?: localResult.parsedIntent.query ?: localResult.parsedIntent.target
                    )
                    handleIntent(
                        intent = localResult.parsedIntent,
                        userQuery = trimmed,
                        executionSource = routingLevel
                    )
                    return@launch
                }

                // STEP 2: FINAL PRE-GEMINI SAFETY CHECK
                // Fallback check on installed apps or search intents before calling Gemini
                val preGeminiFallback = evaluatePreGeminiLocalFallback(trimmed)
                if (preGeminiFallback != null && preGeminiFallback.recognized && preGeminiFallback.parsedIntent != null) {
                    ApiUsageTracker.recordLocalExecution(
                        level = "LEVEL_FINAL_LOCAL_SAFETY_CHECK",
                        intent = preGeminiFallback.intent,
                        queryOrApp = preGeminiFallback.parsedIntent.app ?: preGeminiFallback.parsedIntent.query
                    )
                    handleIntent(
                        intent = preGeminiFallback.parsedIntent,
                        userQuery = trimmed,
                        executionSource = "LEVEL_FINAL_LOCAL_SAFETY_CHECK"
                    )
                    return@launch
                }

                // ====================================================================
                // LEVEL 5 — GEMINI AI ENGINE (Fallback ONLY when local is insufficient)
                // ====================================================================
                ApiUsageTracker.recordGeminiExecution(
                    reason = localResult.reason.takeIf { it.isNotBlank() } ?: "Natural language reasoning required"
                )

                val memoryContext = memoryRepository.buildMemoryContextString()
                val customApiKey = memoryRepository.getCustomApiKey()

                val analysisResult = geminiService.analyzeAndRespond(
                    userInput = trimmed,
                    memoryContext = memoryContext,
                    customApiKey = customApiKey
                )

                if (analysisResult.isSuccess) {
                    val intent = analysisResult.getOrThrow()
                    handleIntent(
                        intent = intent,
                        userQuery = trimmed,
                        executionSource = "LEVEL_5_GEMINI_FALLBACK"
                    )
                } else {
                    val error = analysisResult.exceptionOrNull()
                    handleError(
                        userQuery = trimmed,
                        errorMessage = error?.localizedMessage ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                handleError(
                    userQuery = trimmed,
                    errorMessage = e.localizedMessage ?: "Processing failed"
                )
            }
        }
    }

    /**
     * Final safety gate before invoking Gemini API.
     * Evaluates device package manager, direct search queries, and URLs.
     */
    private fun evaluatePreGeminiLocalFallback(input: String): LocalCommandResult? {
        val lower = input.lowercase(Locale.ROOT).trim()

        // 1. Check if input exactly matches an installed app on device
        try {
            val installedApps = appLauncher.getInstalledApps()
            val matchedApp = installedApps.firstOrNull { app ->
                app.appName.equals(input, ignoreCase = true) ||
                lower.startsWith("open ${app.appName.lowercase(Locale.ROOT)}") ||
                lower.endsWith("${app.appName.lowercase(Locale.ROOT)} kholo") ||
                lower.endsWith("${app.appName.lowercase(Locale.ROOT)} खोलो")
            }
            if (matchedApp != null) {
                return LocalCommandResult(
                    recognized = true,
                    intent = IntentType.OPEN_APP,
                    parsedIntent = ParsedIntent(
                        type = IntentType.OPEN_APP,
                        app = matchedApp.appName,
                        responseText = "${matchedApp.appName} खोला जा रहा है…"
                    ),
                    confidence = 1.0f,
                    reason = "Pre-Gemini fallback matched installed app: ${matchedApp.appName}"
                )
            }
        } catch (_: Exception) {}

        // 2. Direct search keyword check
        val searchPrefixes = listOf("search for ", "look up ", "find ", "गूगल पर खोजो ", "सर्च करो ")
        for (prefix in searchPrefixes) {
            if (lower.startsWith(prefix)) {
                val query = input.substring(prefix.length).trim()
                if (query.isNotBlank()) {
                    return LocalCommandResult(
                        recognized = true,
                        intent = IntentType.WEB_SEARCH,
                        parsedIntent = ParsedIntent(
                            type = IntentType.WEB_SEARCH,
                            query = query,
                            responseText = "Google पर '$query' खोजा जा रहा है…"
                        ),
                        confidence = 1.0f,
                        reason = "Pre-Gemini fallback matched search prefix"
                    )
                }
            }
        }

        return null
    }

    private suspend fun handleIntent(
        intent: ParsedIntent,
        userQuery: String,
        executionSource: String
    ) {
        val activeContext = ConversationContextTracker.getActiveContext()
        val contextEntity = activeContext?.effectiveEntity
        val contextTopic = activeContext?.activeTopicEntity ?: activeContext?.platform

        when (intent.type) {

            IntentType.MULTI_ACTION -> {
                _assistantState.value = AssistantState.EXECUTING_ACTION
                val actionResult = actionManager.executeSequence(
                    actionList = intent.actions,
                    context = context,
                    onProgress = { progress ->
                        _currentActionProgress.value = progress
                    }
                )
                _lastActionResult.value = actionResult

                val replyText = intent.responseText ?: actionResult.message
                memoryRepository.saveAssistantResponse(
                    text = replyText,
                    intent = intent,
                    result = actionResult
                )
                memoryRepository.recordInteraction(
                    userQuery = userQuery,
                    assistantResponse = replyText,
                    intent = intent,
                    result = actionResult,
                    executionSource = executionSource,
                    contextEntity = contextEntity,
                    contextTopic = contextTopic
                )

                _assistantState.value = AssistantState.IDLE
                serviceScope.launch {
                    delay(800L)
                    _currentActionProgress.value = null
                }
            }

            IntentType.OPEN_APP,
            IntentType.OPEN_SETTINGS,
            IntentType.WEB_SEARCH,
            IntentType.YOUTUBE_SEARCH,
            IntentType.YOUTUBE_SEARCH_AND_PLAY,
            IntentType.OPEN_URL,
            IntentType.SET_PREFERENCE,
            IntentType.WAIT,
            IntentType.BACK,
            IntentType.CLEAR_CHAT -> {
                _assistantState.value = AssistantState.EXECUTING_ACTION
                val actionResult = actionManager.executeIntent(
                    intent = intent,
                    context = context,
                    onProgress = { progress ->
                        _currentActionProgress.value = progress
                    }
                )
                _lastActionResult.value = actionResult

                val replyText = if (!actionResult.success) {
                    actionResult.message
                } else if (intent.type == IntentType.YOUTUBE_SEARCH || intent.type == IntentType.YOUTUBE_SEARCH_AND_PLAY) {
                    actionResult.message.takeIf { it.isNotBlank() } ?: (intent.responseText ?: "कार्य पूरा हुआ।")
                } else {
                    intent.responseText ?: actionResult.message
                }

                memoryRepository.saveAssistantResponse(
                    text = replyText,
                    intent = intent,
                    result = actionResult
                )
                memoryRepository.recordInteraction(
                    userQuery = userQuery,
                    assistantResponse = replyText,
                    intent = intent,
                    result = actionResult,
                    executionSource = executionSource,
                    contextEntity = contextEntity,
                    contextTopic = contextTopic
                )

                _assistantState.value = AssistantState.IDLE
                serviceScope.launch {
                    delay(800L)
                    _currentActionProgress.value = null
                }
            }

            IntentType.GENERAL_CHAT,
            IntentType.UNKNOWN -> {
                val replyText = intent.responseText ?: "मायरा तैयार है। बताइए क्या करना है?"
                val result = ActionResult(success = true, message = replyText)
                memoryRepository.saveAssistantResponse(
                    text = replyText,
                    intent = intent,
                    result = result
                )
                memoryRepository.recordInteraction(
                    userQuery = userQuery,
                    assistantResponse = replyText,
                    intent = intent,
                    result = result,
                    executionSource = executionSource,
                    contextEntity = contextEntity,
                    contextTopic = contextTopic
                )
                _assistantState.value = AssistantState.IDLE
            }
        }
    }

    private suspend fun handleError(userQuery: String, errorMessage: String) {
        _assistantState.value = AssistantState.ERROR
        _currentActionProgress.value = null

        val result = ActionResult(success = false, message = errorMessage, error = errorMessage)
        memoryRepository.saveAssistantResponse(
            text = errorMessage,
            result = result
        )
        memoryRepository.recordInteraction(
            userQuery = userQuery,
            assistantResponse = errorMessage,
            result = result,
            executionSource = "ERROR"
        )

        // Reset state to idle after recording error
        _assistantState.value = AssistantState.IDLE
    }

    fun cancelActiveTask() {
        actionChainExecutor.cancelCurrentChain()
        _assistantState.value = AssistantState.IDLE
        _currentActionProgress.value = null
    }

    fun pauseActiveTask() {
        actionChainExecutor.pauseCurrentChain()
    }

    fun resumeActiveTask() {
        actionChainExecutor.resumeCurrentChain()
    }

    fun setBackgroundReady() {
        if (_assistantState.value == AssistantState.IDLE) {
            _assistantState.value = AssistantState.BACKGROUND_READY
        }
    }

    fun setForegroundActive() {
        if (_assistantState.value == AssistantState.BACKGROUND_READY) {
            _assistantState.value = AssistantState.IDLE
        }
    }
}
