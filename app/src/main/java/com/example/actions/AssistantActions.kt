package com.example.actions

import android.content.Context
import android.content.Intent
import com.example.data.MemoryRepository
import com.example.models.ActionProgressUpdate
import com.example.models.ActionResult
import com.example.models.ActionStepStatus
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.platform.android.AppLauncher
import com.example.services.ScreenControlEngine
import kotlinx.coroutines.delay

typealias ICommandAction = AssistantAction

interface AssistantAction {
    val supportedType: IntentType
    suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository? = null,
        onProgress: ((ActionProgressUpdate) -> Unit)? = null
    ): ActionResult
}

class OpenAppAction(private val appLauncher: AppLauncher) : AssistantAction {
    override val supportedType: IntentType = IntentType.OPEN_APP

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val app = intent.app ?: "App"
        return appLauncher.launchAppByName(app)
    }
}

class OpenSettingsAction(private val appLauncher: AppLauncher) : AssistantAction {
    override val supportedType: IntentType = IntentType.OPEN_SETTINGS

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        return appLauncher.launchSettings()
    }
}

class OpenUrlAction(private val appLauncher: AppLauncher) : AssistantAction {
    override val supportedType: IntentType = IntentType.OPEN_URL

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val url = intent.target ?: "https://www.google.com"
        return appLauncher.openUrl(url)
    }
}

class WebSearchAction(private val appLauncher: AppLauncher) : AssistantAction {
    override val supportedType: IntentType = IntentType.WEB_SEARCH

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val query = intent.query ?: intent.target ?: ""
        return appLauncher.performWebSearch(query)
    }
}

class YouTubeSearchAction(
    private val appLauncher: AppLauncher,
    private val screenControlEngine: ScreenControlEngine? = null
) : AssistantAction {
    override val supportedType: IntentType = IntentType.YOUTUBE_SEARCH

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val query = intent.query ?: intent.target ?: ""
        val cleanQuery = query.trim()

        val engine = screenControlEngine ?: ScreenControlEngine(context, appLauncher)

        // Check if Accessibility Service is active for Real Interactive Screen Control (V3.2)
        if (engine.isAccessibilityActive()) {
            val screenResult = engine.executeYouTubeSearchWorkflow(cleanQuery, onProgress)
            if (screenResult.status == com.example.models.ScreenActionStatus.SUCCESS) {
                val resultsDetail = if (screenResult.detectedResults.isNotEmpty()) {
                    "\nपरिणाम (Detected): " + screenResult.detectedResults.joinToString(" • ")
                } else ""
                return ActionResult(
                    success = true,
                    message = screenResult.message + resultsDetail,
                    launchedTarget = "accessibility://youtube/search?q=$cleanQuery"
                )
            }
        }

        // Deterministic V2 intent fallback if accessibility service is not active
        return appLauncher.searchYouTube(cleanQuery)
    }
}

class YouTubeSearchAndPlayAction(
    private val appLauncher: AppLauncher,
    private val screenControlEngine: ScreenControlEngine? = null
) : AssistantAction {
    override val supportedType: IntentType = IntentType.YOUTUBE_SEARCH_AND_PLAY

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val query = intent.query ?: intent.target ?: ""
        val cleanQuery = query.trim()

        val engine = screenControlEngine ?: ScreenControlEngine(context, appLauncher)

        // Check if Accessibility Service is active for Intelligent Screen Control (V3.3)
        if (engine.isAccessibilityActive()) {
            val screenResult = engine.executeYouTubeSearchAndPlayWorkflow(cleanQuery, onProgress)
            if (screenResult.status == com.example.models.ScreenActionStatus.SUCCESS) {
                return ActionResult(
                    success = true,
                    message = screenResult.message,
                    launchedTarget = "accessibility://youtube/play?q=$cleanQuery"
                )
            }
        }

        // Fallback: search YouTube if accessibility service is not enabled
        val fallbackResult = appLauncher.searchYouTube(cleanQuery)
        return ActionResult(
            success = fallbackResult.success,
            message = "${fallbackResult.message} (Screen control unavailable, YouTube search opened directly)",
            launchedTarget = fallbackResult.launchedTarget
        )
    }
}

class WaitAction : AssistantAction {
    override val supportedType: IntentType = IntentType.WAIT

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val duration = (intent.durationMs ?: 1000L).coerceIn(100L, 10000L)
        delay(duration)
        return ActionResult(
            success = true,
            message = "Waiting completed (${duration}ms)",
            launchedTarget = "wait:${duration}ms"
        )
    }
}

class BackAction(
    private val screenControlEngine: ScreenControlEngine? = null
) : AssistantAction {
    override val supportedType: IntentType = IntentType.BACK

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val service = com.example.services.MyraAccessibilityService.getInstance()
        if (service != null && com.example.services.MyraAccessibilityService.isAccessibilityServiceEnabled(context)) {
            val backRes = service.performGlobalBack()
            if (backRes.success) {
                return ActionResult(
                    success = true,
                    message = "Back navigation executed via Accessibility Service.",
                    launchedTarget = "accessibility_global_back"
                )
            }
        }

        return try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            ActionResult(
                success = true,
                message = "Home screen / back navigated",
                launchedTarget = "action_home"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Could not navigate back: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }
}

class SetPreferenceAction : AssistantAction {
    override val supportedType: IntentType = IntentType.SET_PREFERENCE

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        val key = intent.key ?: MemoryRepository.KEY_LANGUAGE
        val value = intent.value ?: "hi"
        memoryRepository?.setPreference(key, value)
        val msg = if (value == "hi") {
            "ठीक है, मैं याद रखूँगी। अब से मैं आपसे हिंदी में बात करूँगी।"
        } else {
            "Noted! I have saved your preference and will communicate in English."
        }
        return ActionResult(
            success = true,
            message = msg,
            launchedTarget = "$key=$value"
        )
    }
}

class ClearChatAction : AssistantAction {
    override val supportedType: IntentType = IntentType.CLEAR_CHAT

    override suspend fun execute(
        intent: ParsedIntent,
        context: Context,
        memoryRepository: MemoryRepository?,
        onProgress: ((ActionProgressUpdate) -> Unit)?
    ): ActionResult {
        memoryRepository?.clearHistory()
        return ActionResult(
            success = true,
            message = "चैट हिस्ट्री साफ़ कर दी गई है। (Chat history cleared)",
            launchedTarget = "action_clear_chat"
        )
    }
}

class ActionManager(
    private val appLauncher: AppLauncher,
    private val memoryRepository: MemoryRepository? = null,
    private val screenControlEngine: ScreenControlEngine? = null
) {
    private val actions = mutableMapOf<IntentType, AssistantAction>()

    init {
        registerAction(OpenAppAction(appLauncher))
        registerAction(OpenSettingsAction(appLauncher))
        registerAction(OpenUrlAction(appLauncher))
        registerAction(WebSearchAction(appLauncher))
        registerAction(YouTubeSearchAction(appLauncher, screenControlEngine))
        registerAction(YouTubeSearchAndPlayAction(appLauncher, screenControlEngine))
        registerAction(WaitAction())
        registerAction(BackAction(screenControlEngine))
        registerAction(ClearChatAction())
        registerAction(SetPreferenceAction())
    }

    fun registerAction(action: AssistantAction) {
        actions[action.supportedType] = action
    }

    fun getAction(type: IntentType): AssistantAction? = actions[type]

    suspend fun executeIntent(
        intent: ParsedIntent,
        context: Context,
        onProgress: ((ActionProgressUpdate) -> Unit)? = null
    ): ActionResult {
        if (intent.type == IntentType.MULTI_ACTION && intent.actions.isNotEmpty()) {
            return executeSequence(intent.actions, context, onProgress)
        }

        val action = actions[intent.type]
        return if (action != null) {
            action.execute(intent, context, memoryRepository, onProgress)
        } else {
            ActionResult(
                success = false,
                message = "Myra can't perform that action yet (${intent.type}).",
                error = "No handler registered for ${intent.type}"
            )
        }
    }

    suspend fun executeSequence(
        actionList: List<ParsedIntent>,
        context: Context,
        onProgress: ((ActionProgressUpdate) -> Unit)? = null
    ): ActionResult {
        if (actionList.isEmpty()) {
            return ActionResult(success = true, message = "No actions to execute.")
        }

        val results = mutableListOf<ActionResult>()
        val totalSteps = actionList.size
        var hasFailure = false

        for ((index, stepIntent) in actionList.withIndex()) {
            val stepNumber = index + 1
            val stepName = when (stepIntent.type) {
                IntentType.OPEN_APP -> "Opening ${stepIntent.app ?: "App"}"
                IntentType.OPEN_SETTINGS -> "Opening Settings"
                IntentType.WEB_SEARCH -> "Searching Google for '${stepIntent.query ?: stepIntent.target}'"
                IntentType.YOUTUBE_SEARCH -> "Searching YouTube for '${stepIntent.query ?: stepIntent.target}'"
                IntentType.YOUTUBE_SEARCH_AND_PLAY -> "Searching YouTube & Playing '${stepIntent.query ?: stepIntent.target}'"
                IntentType.OPEN_URL -> "Opening ${stepIntent.target ?: "URL"}"
                IntentType.WAIT -> "Waiting"
                IntentType.BACK -> "Navigating Back"
                IntentType.SET_PREFERENCE -> "Saving Preference"
                else -> "Executing Step $stepNumber"
            }

            // Report Started
            onProgress?.invoke(
                ActionProgressUpdate(
                    currentStep = stepNumber,
                    totalSteps = totalSteps,
                    stepTitle = stepName,
                    status = ActionStepStatus.STARTED,
                    detailMessage = "Executing $stepName…"
                )
            )

            val actionHandler = actions[stepIntent.type]
            val stepResult = if (actionHandler != null) {
                try {
                    val adaptedProgress: ((ActionProgressUpdate) -> Unit)? = if (onProgress != null && totalSteps > 1) {
                        { subProgress ->
                            onProgress.invoke(
                                ActionProgressUpdate(
                                    currentStep = stepNumber,
                                    totalSteps = totalSteps,
                                    stepTitle = "$stepName (${subProgress.currentStep}/${subProgress.totalSteps})",
                                    status = subProgress.status,
                                    detailMessage = subProgress.detailMessage
                                )
                            )
                        }
                    } else {
                        onProgress
                    }
                    actionHandler.execute(stepIntent, context, memoryRepository, adaptedProgress)
                } catch (e: Exception) {
                    ActionResult(
                        success = false,
                        message = "Step $stepNumber failed: ${e.localizedMessage}",
                        error = e.localizedMessage
                    )
                }
            } else {
                ActionResult(
                    success = false,
                    message = "Myra can't perform that action yet (${stepIntent.type}).",
                    error = "Unsupported action type: ${stepIntent.type}"
                )
            }

            results.add(stepResult)

            if (stepResult.success) {
                onProgress?.invoke(
                    ActionProgressUpdate(
                        currentStep = stepNumber,
                        totalSteps = totalSteps,
                        stepTitle = stepName,
                        status = ActionStepStatus.COMPLETED,
                        detailMessage = "✓ ${stepResult.message}"
                    )
                )
                // 5-SECOND DELAY ONLY FOR MULTI-ACTION COMMANDS (between sequential actions, excluding final step)
                if (index < actionList.size - 1) {
                    delay(5000L)
                }
            } else {
                hasFailure = true
                onProgress?.invoke(
                    ActionProgressUpdate(
                        currentStep = stepNumber,
                        totalSteps = totalSteps,
                        stepTitle = stepName,
                        status = ActionStepStatus.FAILED,
                        detailMessage = "✗ ${stepResult.message}"
                    )
                )

                // If one queued action fails, stop the remaining queued actions immediately
                break
            }
        }

        val allSuccessful = !hasFailure && results.all { it.success }
        val summaryMessage = if (allSuccessful) {
            "All ${results.size} actions completed successfully."
        } else {
            val failedCount = results.count { !it.success }
            "Completed with $failedCount issue(s). ${results.lastOrNull()?.message ?: ""}"
        }

        return ActionResult(
            success = allSuccessful,
            message = summaryMessage,
            stepResults = results
        )
    }
}
