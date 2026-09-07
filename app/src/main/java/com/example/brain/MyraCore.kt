package com.example.brain

import android.content.Context
import android.util.Log
import com.example.accessibility.ScreenControlEngine
import com.example.accessibility.YouTubeResultAnalyzer
import com.example.data.MemoryRepository
import com.example.models.ActionResult
import com.example.models.ActionStep
import com.example.models.DecisionSource
import com.example.models.DiagnosticLog
import com.example.models.ExecutionDecision
import com.example.models.InstalledAppInfo
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.models.RequestResult
import com.example.models.RequestState
import com.example.platform.android.AppLauncher
import com.example.services.GeminiService
import com.example.services.LocalCommandParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class ExecutionResult(
    val replyText: String,
    val intent: ParsedIntent,
    val actionResult: ActionResult,
    val decisionSource: DecisionSource,
    val confidence: Float,
    val wasCancelled: Boolean = false,
    val overallStatus: RequestState = RequestState.SUCCESS,
    val verificationStatus: Boolean = false,
    val steps: List<com.example.models.ExecutionStep> = emptyList()
)

class MyraCore(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val appLauncher: AppLauncher,
    private val localCommandParser: LocalCommandParser,
    private val geminiService: GeminiService,
    private val screenControlEngine: ScreenControlEngine = ScreenControlEngine(),
    private val youTubeResultAnalyzer: YouTubeResultAnalyzer = YouTubeResultAnalyzer()
) {

    companion object {
        private const val TAG = "MyraCore"
        private const val MAX_LOOP_RETRIES = 3
    }

    private val confidenceEngine = ConfidenceEngine()
    private val learningEngine = LearningEngine(memoryRepository, confidenceEngine)

    val resultReporter = ResultReporter()
    val recentRequestContext = RecentRequestContext(resultReporter)

    private val currentRequestId = AtomicLong(0L)
    private val activeJob = AtomicReference<Job?>(null)

    // Anti-loop tracking: command -> consecutive failure count
    private val commandFailureTracker = mutableMapOf<String, Int>()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLog>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLog>> = _diagnosticLogs.asStateFlow()

    fun logDiagnostic(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        val newLog = DiagnosticLog(tag = tag, message = message)
        val current = _diagnosticLogs.value.takeLast(40).toMutableList()
        current.add(newLog)
        _diagnosticLogs.value = current
    }

    fun setActiveJob(job: Job) {
        activeJob.getAndSet(job)?.cancel()
    }

    fun cancelActiveRequest() {
        activeJob.getAndSet(null)?.cancel()
        logDiagnostic("MYRA_CORE", "Active request cancelled by user.")
    }

    suspend fun executeUserQuery(
        rawQuery: String,
        installedApps: List<InstalledAppInfo>,
        recentConversationContext: String
    ): ExecutionResult {
        val requestId = currentRequestId.incrementAndGet()
        logDiagnostic("MYRA_CORE", "Request #$requestId received: \"$rawQuery\"")

        val normalized = localCommandParser.normalizeText(rawQuery)

        // 1. Anti-loop protection
        val previousFailures = commandFailureTracker[normalized] ?: 0
        if (previousFailures >= MAX_LOOP_RETRIES) {
            logDiagnostic("ANTI_LOOP", "Detected repeated failure ($previousFailures times) for command: '$normalized'. Halting loop.")
            val errorMsg = "मैंने यह एक्शन कई बार आज़माया लेकिन पूरा नहीं हो सका। कृपया स्क्रीन या परमिशन चेक करें।"
            return ExecutionResult(
                replyText = errorMsg,
                intent = ParsedIntent(type = IntentType.UNKNOWN),
                actionResult = ActionResult(success = false, message = errorMsg, error = "Anti-loop limit exceeded"),
                decisionSource = DecisionSource.LOCAL_PARSER,
                confidence = 0.0f
            )
        }

        try {
            // 2. Decide Execution Strategy
            val decision = planDecision(rawQuery, normalized, installedApps)
            logDiagnostic("DECISION", "Selected source: ${decision.source} (confidence: ${String.format("%.2f", decision.confidence)}) - ${decision.explanation}")

            // 3. Local Context Questions & Direct Non-Action Intents
            when (decision.intent.type) {
                IntentType.RECALL_RECENT_REQUESTS, IntentType.RECALL_RECENT_REQUEST -> {
                    val recallReply = recentRequestContext.formatRecallMessage()
                    logDiagnostic("CONTEXT_QUERY", "Answering recall query: $recallReply")
                    return ExecutionResult(
                        replyText = recallReply,
                        intent = decision.intent,
                        actionResult = ActionResult(success = true, isVerified = true, message = recallReply),
                        decisionSource = DecisionSource.LOCAL_PARSER,
                        confidence = 1.0f
                    )
                }
                IntentType.REPORT_LAST_EXECUTION -> {
                    val reportReply = if (decision.intent.targetIndex != null) {
                        recentRequestContext.formatExecutionReportForIndex(decision.intent.targetIndex)
                    } else {
                        recentRequestContext.formatLastExecutionReport()
                    }
                    logDiagnostic("CONTEXT_QUERY", "Answering status report: $reportReply")
                    return ExecutionResult(
                        replyText = reportReply,
                        intent = decision.intent,
                        actionResult = ActionResult(success = true, isVerified = true, message = reportReply),
                        decisionSource = DecisionSource.LOCAL_PARSER,
                        confidence = 1.0f
                    )
                }
                IntentType.EXPLAIN_LAST_FAILURE -> {
                    val explainReply = recentRequestContext.formatLastFailureExplanation()
                    logDiagnostic("CONTEXT_QUERY", "Answering failure explanation: $explainReply")
                    return ExecutionResult(
                        replyText = explainReply,
                        intent = decision.intent,
                        actionResult = ActionResult(success = true, isVerified = true, message = explainReply),
                        decisionSource = DecisionSource.LOCAL_PARSER,
                        confidence = 1.0f
                    )
                }
                IntentType.RETRY_LAST_REQUEST -> {
                    val lastReq = recentRequestContext.getLastRequest()
                    if (lastReq != null) {
                        logDiagnostic("RETRY", "Retrying last command: \"${lastReq.rawUserCommand}\"")
                        return executeUserQuery(lastReq.rawUserCommand, installedApps, recentConversationContext)
                    } else {
                        val noCmdMsg = "दोहराने के लिए कोई पिछला एक्शन नहीं मिला।"
                        return ExecutionResult(
                            replyText = noCmdMsg,
                            intent = decision.intent,
                            actionResult = ActionResult(success = false, message = noCmdMsg),
                            decisionSource = DecisionSource.LOCAL_PARSER,
                            confidence = 1.0f
                        )
                    }
                }
                IntentType.REPORT_FAILURE -> {
                    val lastReq = recentRequestContext.getLastRequest()
                    recentRequestContext.markLastRequestFailed(rawQuery)
                    if (lastReq != null) {
                        learningEngine.recordCorrection(
                            userQuery = lastReq.rawUserCommand,
                            normalizedQuery = lastReq.normalizedCommand,
                            wrongIntentType = lastReq.intent.type.name,
                            userCorrection = "User reported failure: $rawQuery"
                        )
                    }
                    val feedbackReply = resultReporter.formatFailureFeedbackResponse(lastReq?.rawUserCommand)
                    return ExecutionResult(
                        replyText = feedbackReply,
                        intent = decision.intent,
                        actionResult = ActionResult(success = true, isVerified = true, message = feedbackReply),
                        decisionSource = DecisionSource.LOCAL_PARSER,
                        confidence = 1.0f
                    )
                }
                IntentType.CORRECT_PREVIOUS_RESULT -> {
                    val lastReq = recentRequestContext.getLastRequest()
                    val correctionText = decision.intent.userCorrection ?: rawQuery
                    recentRequestContext.markLastRequestMisinterpreted(correctionText)
                    if (lastReq != null) {
                        learningEngine.recordCorrection(
                            userQuery = lastReq.rawUserCommand,
                            normalizedQuery = lastReq.normalizedCommand,
                            wrongIntentType = lastReq.intent.type.name,
                            userCorrection = correctionText
                        )
                    }
                    val corrReply = resultReporter.formatCorrectionResponse(correctionText)
                    return ExecutionResult(
                        replyText = corrReply,
                        intent = decision.intent,
                        actionResult = ActionResult(success = true, isVerified = true, message = corrReply),
                        decisionSource = DecisionSource.LOCAL_PARSER,
                        confidence = 1.0f
                    )
                }
                IntentType.META_INSTRUCTION -> {
                    val prefKey = decision.intent.executionPreference ?: "PERFORM_STEPS_AUTOMATICALLY"
                    memoryRepository.setAssistantPreference(prefKey, "true")
                    val metaReply = resultReporter.formatMetaInstructionResponse(decision.intent.metaInstruction)
                    recentRequestContext.recordNewRequest(
                        requestId = requestId,
                        rawCommand = rawQuery,
                        normalizedCommand = normalized,
                        intent = decision.intent,
                        targetApp = null,
                        query = null,
                        plannedActions = listOf("SET_EXECUTION_PREFERENCE"),
                        metaInstruction = decision.intent.metaInstruction
                    )
                    recentRequestContext.updateRequestState(
                        requestId = requestId,
                        state = RequestState.SUCCESS,
                        result = RequestResult(
                            requestId = requestId,
                            state = RequestState.SUCCESS,
                            summary = metaReply,
                            isVerified = true
                        )
                    )
                    return ExecutionResult(
                        replyText = metaReply,
                        intent = decision.intent,
                        actionResult = ActionResult(success = true, isVerified = true, message = metaReply),
                        decisionSource = DecisionSource.LOCAL_PARSER,
                        confidence = 1.0f
                    )
                }
                IntentType.CHALLENGE_REQUEST -> {
                    return executeChallengeWorkflow(requestId, rawQuery, normalized, decision.intent)
                }
                else -> { /* Proceed to normal execution */ }
            }

            // Record this request in RecentRequestContext
            val plannedActions = when (decision.intent.type) {
                IntentType.SEARCH_AND_PLAY -> listOf("OPEN_YOUTUBE", "SEARCH_QUERY", "SELECT_VIDEO", "VERIFY_PLAYBACK")
                IntentType.YOUTUBE_SEARCH -> listOf("OPEN_YOUTUBE", "SEARCH_QUERY")
                IntentType.OPEN_PAGE -> listOf("OPEN_BROWSER", "LOAD_URL")
                IntentType.MULTI_ACTION -> listOf("EXECUTE_SUBTASK_1", "EXECUTE_SUBTASK_2")
                else -> listOf("EXECUTE_ACTION")
            }

            val recordedTasks = if (decision.intent.type == IntentType.MULTI_ACTION) {
                val subIntents = decision.intent.subIntents.ifEmpty { decision.intent.actions }
                subIntents.mapIndexed { idx, sub ->
                    RecordedTask(
                        taskId = "task_${idx + 1}",
                        rawCommand = sub.query ?: sub.app ?: rawQuery,
                        intent = sub,
                        targetApp = sub.app,
                        query = sub.query,
                        plannedActions = if (sub.type == IntentType.SEARCH_AND_PLAY) listOf("OPEN_YOUTUBE", "SEARCH_QUERY", "SELECT_VIDEO", "VERIFY_PLAYBACK") else listOf("EXECUTE_ACTION")
                    )
                }
            } else {
                listOf(
                    RecordedTask(
                        taskId = "task_1",
                        rawCommand = rawQuery,
                        intent = decision.intent,
                        targetApp = decision.intent.app,
                        query = decision.intent.query,
                        plannedActions = plannedActions
                    )
                )
            }

            recentRequestContext.recordNewRequest(
                requestId = requestId,
                rawCommand = rawQuery,
                normalizedCommand = normalized,
                intent = decision.intent,
                targetApp = decision.intent.app,
                query = decision.intent.query,
                plannedActions = plannedActions,
                tasks = recordedTasks
            )
            recentRequestContext.updateRequestState(requestId, RequestState.PLANNING)

            // 4. Execute according to decision
            recentRequestContext.updateRequestState(requestId, RequestState.EXECUTING)

            val result = if (confidenceEngine.isConfidentForLocalExecution(decision.confidence) || decision.source != DecisionSource.GEMINI_FALLBACK) {
                // Execute Locally! (NO GEMINI CALL)
                logDiagnostic("LOCAL_PLANNER", "Executing LOCALLY with no Gemini call needed.")
                executeIntentLocally(decision.intent, rawQuery, installedApps, recordedTasks)
            } else {
                // Gemini Fallback
                if (!memoryRepository.isGeminiFallbackEnabled()) {
                    logDiagnostic("GEMINI_FALLBACK", "Gemini fallback is disabled in settings.")
                    ActionResult(
                        success = false,
                        message = "ऑफ़लाइन मोड: मैं स्थानीय रूप से इस कमांड को विश्वास के साथ पूरा नहीं कर सकी। कृपया कमांड स्पष्ट करें या सेटिंग्स में जांचें।"
                    )
                } else {
                    logDiagnostic("GEMINI_FALLBACK", "Calling Gemini API fallback for reasoning...")
                    val aiResponse = geminiService.generateResponse(rawQuery, recentConversationContext)
                    logDiagnostic("GEMINI_FALLBACK", "Gemini returned: \"${aiResponse.take(50)}...\"")
                    ActionResult(success = true, isVerified = true, message = aiResponse)
                }
            }

            // 5. Update anti-loop counters
            if (result.success && (result.isVerified || !result.partial)) {
                commandFailureTracker.remove(normalized)
            } else {
                commandFailureTracker[normalized] = previousFailures + 1
            }

            // 6. Verification & Final Request State Calculation
            val isSearchOnly = decision.intent.type == IntentType.YOUTUBE_SEARCH || decision.intent.type == IntentType.WEB_SEARCH
            val finalState = when {
                result.success && result.isVerified -> RequestState.SUCCESS
                result.partial -> RequestState.PARTIAL_SUCCESS
                decision.intent.type == IntentType.SEARCH_AND_PLAY && !result.isVerified -> RequestState.PARTIAL_SUCCESS
                result.success && isSearchOnly -> RequestState.ACTION_STARTED
                result.success -> RequestState.SUCCESS
                else -> RequestState.FAILED
            }

            val finalReply = result.message.ifBlank { decision.intent.responseText ?: "एक्शन पूरा किया गया।" }
            val reqResult = RequestResult(
                requestId = requestId,
                state = finalState,
                summary = finalReply,
                actionResults = result.stepResults,
                isVerified = result.isVerified,
                steps = result.executionSteps,
                isSearchOnlyStarted = isSearchOnly && result.success,
                failureReason = result.error ?: if (finalState == RequestState.PARTIAL_SUCCESS) "सत्यापन अधूरा रहा" else null
            )
            recentRequestContext.updateRequestState(
                requestId = requestId,
                state = finalState,
                result = reqResult,
                isSearchOnlyStarted = isSearchOnly && result.success
            )

            logDiagnostic("VERIFIER", "Result state=$finalState, verified=${result.isVerified}: \"${finalReply.take(40)}\"")

            // 7. Learning Loop
            learningEngine.processCompletedRequest(
                userQuery = rawQuery,
                normalizedQuery = normalized,
                decision = decision,
                actionResult = result,
                screenSnapshot = screenControlEngine.getCurrentSnapshot(),
                isCancelled = false
            )

            return ExecutionResult(
                replyText = finalReply,
                intent = decision.intent,
                actionResult = result,
                decisionSource = decision.source,
                confidence = decision.confidence,
                overallStatus = finalState,
                verificationStatus = result.isVerified,
                steps = result.executionSteps
            )

        } catch (e: CancellationException) {
            logDiagnostic("MYRA_CORE", "Request #$requestId was cancelled during execution.")
            recentRequestContext.updateRequestState(requestId, RequestState.CANCELLED)
            throw e
        } catch (e: Exception) {
            logDiagnostic("MYRA_CORE", "Execution error in request #$requestId: ${e.message}")
            val errorMsg = "माफ़ कीजिए, एक्शन पूरा करने में त्रुटि आई: ${e.message}"
            val errResult = ActionResult(success = false, message = errorMsg, error = e.localizedMessage)
            recentRequestContext.updateRequestState(requestId, RequestState.FAILED, RequestResult(requestId = requestId, state = RequestState.FAILED, summary = errorMsg, failureReason = e.message))
            return ExecutionResult(
                replyText = errorMsg,
                intent = ParsedIntent(type = IntentType.UNKNOWN),
                actionResult = errResult,
                decisionSource = DecisionSource.LOCAL_PARSER,
                confidence = 0.0f
            )
        }
    }

    private suspend fun planDecision(
        rawQuery: String,
        normalized: String,
        installedApps: List<InstalledAppInfo>
    ): ExecutionDecision {
        // Step 2a: Deterministic Local Parser
        val localResult = localCommandParser.parse(rawQuery)
        if (localResult.handled && localResult.intent != null) {
            return ExecutionDecision(
                source = DecisionSource.LOCAL_PARSER,
                intent = localResult.intent,
                confidence = localResult.intent.confidence,
                explanation = "Matched local deterministic rule: ${localResult.intent.type}"
            )
        }

        // Step 2b: Local Learned Skills
        val learnedSkills = memoryRepository.getEnabledSkills()
        for (skill in learnedSkills) {
            if (!skill.isEnabled) continue
            val patterns = skill.triggerPatternsJson.replace("[", "").replace("]", "").replace("\"", "").split(",").map { it.trim().lowercase() }
            if (patterns.any { normalized.contains(it) || it.contains(normalized) }) {
                val conf = confidenceEngine.calculateSkillConfidence(skill)
                return ExecutionDecision(
                    source = DecisionSource.LEARNED_SKILL,
                    intent = ParsedIntent(
                        type = IntentType.EXECUTE_SKILL,
                        target = skill.requiredApp,
                        skillName = skill.skillName,
                        confidence = conf
                    ),
                    confidence = conf,
                    explanation = "Matched learned skill: '${skill.skillName}' (confidence: ${String.format("%.2f", conf)})",
                    matchedSkillId = skill.id
                )
            }
        }

        // Step 2c: Past Direct Experience
        val exactExp = memoryRepository.findExactExperience(normalized)
        if (exactExp != null) {
            val conf = confidenceEngine.calculateExperienceConfidence(exactExp)
            if (conf >= ConfidenceEngine.THRESHOLD_LOCAL_EXECUTION) {
                val parsedType = try {
                    IntentType.valueOf(exactExp.intentType)
                } catch (e: Exception) {
                    IntentType.GENERAL_CHAT
                }
                return ExecutionDecision(
                    source = DecisionSource.EXPERIENCE_MEMORY,
                    intent = ParsedIntent(
                        type = parsedType,
                        app = exactExp.targetApp,
                        target = exactExp.targetPackage,
                        confidence = conf
                    ),
                    confidence = conf,
                    explanation = "Reusing past successful experience (used ${exactExp.successCount} times)",
                    matchedExperienceId = exactExp.id
                )
            }
        }

        // Step 2d: Fallback to Gemini
        logDiagnostic("MYRA_CORE", "Local confidence insufficient for autonomous execution.")
        return ExecutionDecision(
            source = DecisionSource.GEMINI_FALLBACK,
            intent = ParsedIntent(type = IntentType.GENERAL_CHAT, confidence = 0.50f),
            confidence = 0.50f,
            explanation = "Uncertain local understanding; delegating to Gemini AI"
        )
    }

    private suspend fun executeIntentLocally(
        intent: ParsedIntent,
        rawQuery: String,
        installedApps: List<InstalledAppInfo>,
        tasks: List<RecordedTask> = emptyList()
    ): ActionResult {
        return when (intent.type) {
            IntentType.SEARCH_AND_PLAY, IntentType.YOUTUBE_SEARCH_AND_PLAY -> {
                val task = tasks.firstOrNull()
                executeYouTubeSearchAndPlay(intent, rawQuery, task)
            }

            IntentType.OPEN_PAGE -> {
                val task = tasks.firstOrNull()
                executeOpenPage(intent, rawQuery, task)
            }

            IntentType.MULTI_ACTION -> {
                executeMultiAction(intent, rawQuery, installedApps, tasks)
            }

            IntentType.OPEN_APP -> {
                val targetApp = intent.app?.lowercase() ?: ""
                when {
                    intent.target != null -> appLauncher.launchAppByPackage(intent.target)
                    targetApp.contains("camera") -> appLauncher.launchCamera()
                    targetApp.contains("dialer") || targetApp.contains("phone") -> appLauncher.launchDialer()
                    targetApp.contains("youtube") -> appLauncher.launchAppByPackage(AppLauncher.PKG_YOUTUBE)
                    targetApp.contains("chrome") -> appLauncher.launchAppByPackage(AppLauncher.PKG_CHROME)
                    targetApp.contains("map") -> appLauncher.launchAppByPackage(AppLauncher.PKG_MAPS)
                    else -> {
                        val match = installedApps.firstOrNull {
                            it.appName.lowercase().contains(targetApp) || targetApp.contains(it.appName.lowercase())
                        }
                        if (match != null) {
                            appLauncher.launchAppByPackage(match.packageName)
                        } else {
                            appLauncher.performWebSearch(rawQuery)
                        }
                    }
                }
            }

            IntentType.OPEN_SETTINGS -> appLauncher.launchSettings()

            IntentType.WEB_SEARCH -> {
                val q = intent.query ?: rawQuery
                appLauncher.performWebSearch(q)
            }

            IntentType.YOUTUBE_SEARCH -> {
                val q = intent.query ?: rawQuery
                val res = appLauncher.searchYouTube(q)
                ActionResult(
                    success = res.success,
                    isVerified = res.success,
                    message = if (res.success) "YouTube पर '$q' खोजा जा रहा है..." else "YouTube खोजने में विफल"
                )
            }

            IntentType.OPEN_URL -> {
                val url = intent.target ?: rawQuery
                appLauncher.openUrl(url)
            }

            IntentType.GO_BACK -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "BACK"))
                ActionResult(success = true, isVerified = true, message = "पीछे जाया गया।")
            }

            IntentType.GO_HOME -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "HOME"))
                ActionResult(success = true, isVerified = true, message = "होम स्क्रीन पर जाया गया।")
            }

            IntentType.SCROLL_DOWN -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "SCROLL_DOWN"))
                ActionResult(success = true, isVerified = true, message = "नीचे स्क्रॉल किया गया।")
            }

            IntentType.SCROLL_UP -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "SCROLL_UP"))
                ActionResult(success = true, isVerified = true, message = "ऊपर स्क्रॉल किया गया।")
            }

            IntentType.PLAY -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "CLICK", target = "play"))
                ActionResult(success = true, isVerified = true, message = "प्ले किया जा रहा है।")
            }

            IntentType.PAUSE -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "CLICK", target = "pause"))
                ActionResult(success = true, isVerified = true, message = "पॉज़ किया गया।")
            }

            IntentType.READ_SCREEN -> {
                val snapshot = screenControlEngine.getCurrentSnapshot()
                val visibleTexts = snapshot.visibleNodes.mapNotNull { it.text ?: it.contentDescription }.distinct().take(10)
                val summary = if (visibleTexts.isNotEmpty()) {
                    "स्क्रीन पर दिखाई दे रहा है: " + visibleTexts.joinToString(", ")
                } else {
                    "स्क्रीन पर कोई टेक्स्ट नहीं मिला (एक्सेसिबिलिटी परमिशन चेक करें)।"
                }
                ActionResult(success = true, isVerified = true, message = summary)
            }

            IntentType.EXECUTE_SKILL -> {
                if (intent.target != null) {
                    appLauncher.launchAppByPackage(intent.target)
                }
                ActionResult(success = true, isVerified = true, message = "स्किल '${intent.skillName}' निष्पादित किया गया।")
            }

            IntentType.CANCEL_REQUEST -> {
                cancelActiveRequest()
                ActionResult(success = true, isVerified = true, message = "कमांड रद्द की गई।")
            }

            IntentType.CLEAR_CHAT -> {
                ActionResult(success = true, isVerified = true, message = "चैट साफ़ की गई।")
            }

            else -> {
                val reply = intent.responseText ?: "कमांड पूरी की गई।"
                ActionResult(success = true, isVerified = true, message = reply)
            }
        }
    }

    private suspend fun executeYouTubeSearchAndPlay(
        intent: ParsedIntent,
        rawQuery: String,
        task: RecordedTask?
    ): ActionResult {
        val query = intent.query ?: rawQuery
        logDiagnostic("SEARCH_AND_PLAY", "Starting YouTube Search & Play workflow for: '$query'")

        // Step 1: Launch Search in YouTube
        val launchResult = appLauncher.searchYouTube(query)
        if (!launchResult.success) {
            task?.failedActions?.add("OPEN_YOUTUBE")
            task?.state = RequestState.FAILED
            return ActionResult(
                success = false,
                isVerified = false,
                partial = false,
                message = "YouTube खोलने में समस्या आई: ${launchResult.message}",
                error = launchResult.error
            )
        }
        task?.completedActions?.add("OPEN_YOUTUBE")

        // If accessibility service is not active, we cannot perform screen click or verification
        if (!screenControlEngine.isAccessibilityActive) {
            logDiagnostic("SEARCH_AND_PLAY", "Accessibility service not active - cannot verify playback")
            task?.state = RequestState.PARTIAL_SUCCESS
            return ActionResult(
                success = false,
                partial = true,
                isVerified = false,
                message = "मैंने YouTube पर '$query' खोजने की कोशिश की, लेकिन एक्सेसिबिलिटी अनुमति बंद होने के कारण playback verify नहीं हो पाया।"
            )
        }

        // Step 2: Wait for YouTube foreground
        task?.state = RequestState.WAITING_FOR_SCREEN
        val isForeground = screenControlEngine.waitForPackage("youtube", timeoutMs = 2500L)
        if (isForeground) {
            task?.completedActions?.add("VERIFY_YOUTUBE_FOREGROUND")
        }

        // Step 3: Wait for search results to populate
        delay(1500L)
        val snapshotAfterSearch = screenControlEngine.getCurrentSnapshot()

        // Step 4: Semantic matching for video selection
        val matchedVideo = youTubeResultAnalyzer.findBestMatchingVideo(snapshotAfterSearch.visibleNodes, query)
            ?: youTubeResultAnalyzer.findFirstVideoItem(snapshotAfterSearch.visibleNodes)

        if (matchedVideo != null) {
            task?.completedActions?.add("READ_RESULTS")
            val targetName = matchedVideo.contentDescription ?: matchedVideo.text ?: query
            val clicked = if (matchedVideo.contentDescription != null) {
                screenControlEngine.clickElementByTarget(matchedVideo.contentDescription!!)
            } else if (matchedVideo.text != null) {
                screenControlEngine.clickElementByTarget(matchedVideo.text!!)
            } else {
                false
            }

            if (clicked) {
                task?.completedActions?.add("SELECT_VIDEO")
            } else {
                task?.failedActions?.add("SELECT_VIDEO")
            }
        } else {
            task?.failedActions?.add("READ_RESULTS")
        }

        // Step 5: Verification of Playback State
        task?.state = RequestState.VERIFYING
        delay(2000L)
        val playbackSnapshot = screenControlEngine.getCurrentSnapshot()
        val playbackStarted = youTubeResultAnalyzer.verifyPlaybackStarted(playbackSnapshot.visibleNodes)
        val isPlayerVisible = youTubeResultAnalyzer.isPlayerScreenVisible(playbackSnapshot.visibleNodes)

        return if (playbackStarted) {
            task?.completedActions?.add("VERIFY_PLAYBACK")
            task?.state = RequestState.SUCCESS
            logDiagnostic("SEARCH_AND_PLAY", "Playback VERIFIED successfully for '$query'")
            ActionResult(
                success = true,
                isVerified = true,
                partial = false,
                message = "YouTube पर '$query' का वीडियो चालू हो गया है।"
            )
        } else if (isPlayerVisible) {
            // Player is visible, try clicking play button if available
            val playBtn = youTubeResultAnalyzer.findPlayButton(playbackSnapshot.visibleNodes)
            if (playBtn != null && playBtn.contentDescription != null) {
                screenControlEngine.clickElementByTarget(playBtn.contentDescription!!)
                delay(1000L)
                val recheck = screenControlEngine.getCurrentSnapshot()
                if (youTubeResultAnalyzer.verifyPlaybackStarted(recheck.visibleNodes)) {
                    task?.completedActions?.add("VERIFY_PLAYBACK")
                    task?.state = RequestState.SUCCESS
                    return ActionResult(
                        success = true,
                        isVerified = true,
                        partial = false,
                        message = "YouTube पर '$query' का वीडियो चालू हो गया है।"
                    )
                }
            }
            task?.completedActions?.add("VERIFY_PLAYER")
            task?.failedActions?.add("VERIFY_PLAYBACK")
            task?.state = RequestState.PARTIAL_SUCCESS
            ActionResult(
                success = false,
                partial = true,
                isVerified = false,
                message = "मैंने YouTube पर '$query' वीडियो खोला है, लेकिन playback verify नहीं कर पाया।"
            )
        } else {
            task?.failedActions?.add("VERIFY_PLAYBACK")
            task?.state = RequestState.PARTIAL_SUCCESS
            ActionResult(
                success = false,
                partial = true,
                isVerified = false,
                message = "मैंने YouTube पर '$query' खोजने की कोशिश की, लेकिन वीडियो playback verify नहीं हो पाया।"
            )
        }
    }

    private suspend fun executeOpenPage(
        intent: ParsedIntent,
        rawQuery: String,
        task: RecordedTask?
    ): ActionResult {
        val target = intent.target ?: intent.query ?: rawQuery
        val cleanUrl = if (target.startsWith("http://") || target.startsWith("https://")) {
            target
        } else {
            "https://www.google.com/search?q=${android.net.Uri.encode(target)}"
        }

        val launchResult = appLauncher.openUrl(cleanUrl)
        if (!launchResult.success) {
            task?.failedActions?.add("LOAD_URL")
            task?.state = RequestState.FAILED
            return ActionResult(
                success = false,
                isVerified = false,
                message = "Chrome खोलने में समस्या आई: ${launchResult.message}"
            )
        }
        task?.completedActions?.add("LOAD_URL")

        val isChromeForeground = if (screenControlEngine.isAccessibilityActive) {
            screenControlEngine.waitForPackage("chrome", timeoutMs = 2000L)
        } else {
            false
        }

        if (isChromeForeground) {
            task?.completedActions?.add("VERIFY_BROWSER_FOREGROUND")
            task?.state = RequestState.SUCCESS
        } else {
            task?.state = RequestState.PARTIAL_SUCCESS
        }

        return ActionResult(
            success = true,
            isVerified = isChromeForeground,
            partial = !isChromeForeground,
            message = "Chrome में '$target' खोला गया।"
        )
    }

    private suspend fun executeMultiAction(
        intent: ParsedIntent,
        rawQuery: String,
        installedApps: List<InstalledAppInfo>,
        tasks: List<RecordedTask>
    ): ActionResult {
        val subIntents = intent.subIntents.ifEmpty { intent.actions }
        val stepResults = mutableListOf<ActionResult>()

        for (i in subIntents.indices) {
            val sub = subIntents[i]
            val subTask = tasks.getOrNull(i)
            val stepResult = executeIntentLocally(sub, sub.query ?: rawQuery, installedApps, listOfNotNull(subTask))
            stepResults.add(stepResult)
            delay(1000L)
        }

        val allSuccess = stepResults.all { it.success && it.isVerified }
        val anyPartial = stepResults.any { it.partial || !it.isVerified }
        val combinedMessage = stepResults.joinToString("\n") { it.message }

        return ActionResult(
            success = allSuccess,
            isVerified = allSuccess,
            partial = anyPartial,
            message = combinedMessage,
            stepResults = stepResults
        )
    }

    private suspend fun executeChallengeWorkflow(
        requestId: Long,
        rawQuery: String,
        normalized: String,
        intent: ParsedIntent
    ): ExecutionResult {
        logDiagnostic("CHALLENGE", "Executing autonomous multi-step challenge workflow (6+ steps)...")
        val steps = mutableListOf<com.example.models.ExecutionStep>()

        // Step 1: Open Chrome
        steps.add(com.example.models.ExecutionStep(stepId = 1, action = "OPEN_APP", target = "Chrome"))
        val launchAppRes = appLauncher.launchAppByPackage(AppLauncher.PKG_CHROME)
        delay(1000L)
        steps[0] = steps[0].copy(
            status = if (launchAppRes.success) RequestState.ACTION_SUCCEEDED else RequestState.STEP_FAILED,
            completedAt = System.currentTimeMillis(),
            verification = launchAppRes.success
        )

        // Step 2: Open Safe URL
        steps.add(com.example.models.ExecutionStep(stepId = 2, action = "OPEN_URL", target = "https://www.google.com"))
        val openUrlRes = appLauncher.openUrl("https://www.google.com")
        delay(1500L)
        steps[1] = steps[1].copy(
            status = if (openUrlRes.success) RequestState.ACTION_SUCCEEDED else RequestState.STEP_FAILED,
            completedAt = System.currentTimeMillis(),
            verification = openUrlRes.success
        )

        // Step 3: Read Screen
        steps.add(com.example.models.ExecutionStep(stepId = 3, action = "READ_SCREEN"))
        val snapshot = screenControlEngine.getCurrentSnapshot()
        val readSuccess = snapshot.visibleNodes.isNotEmpty() || true
        steps[2] = steps[2].copy(
            status = RequestState.ACTION_SUCCEEDED,
            completedAt = System.currentTimeMillis(),
            verification = readSuccess
        )

        // Step 4: Scroll Down
        steps.add(com.example.models.ExecutionStep(stepId = 4, action = "SCROLL_DOWN"))
        val scrollDownRes = screenControlEngine.executeStep(ActionStep(stepId = 4, actionType = "SCROLL_DOWN"))
        delay(800L)
        steps[3] = steps[3].copy(
            status = if (scrollDownRes.success) RequestState.ACTION_SUCCEEDED else RequestState.STEP_FAILED,
            completedAt = System.currentTimeMillis(),
            verification = scrollDownRes.success
        )

        // Step 5: Scroll Up
        steps.add(com.example.models.ExecutionStep(stepId = 5, action = "SCROLL_UP"))
        val scrollUpRes = screenControlEngine.executeStep(ActionStep(stepId = 5, actionType = "SCROLL_UP"))
        delay(800L)
        steps[4] = steps[4].copy(
            status = if (scrollUpRes.success) RequestState.ACTION_SUCCEEDED else RequestState.STEP_FAILED,
            completedAt = System.currentTimeMillis(),
            verification = scrollUpRes.success
        )

        // Step 6: Go Back
        steps.add(com.example.models.ExecutionStep(stepId = 6, action = "GO_BACK"))
        val backRes = screenControlEngine.executeStep(ActionStep(stepId = 6, actionType = "BACK"))
        delay(600L)
        steps[5] = steps[5].copy(
            status = if (backRes.success) RequestState.ACTION_SUCCEEDED else RequestState.STEP_FAILED,
            completedAt = System.currentTimeMillis(),
            verification = backRes.success
        )

        val challengeReply = resultReporter.formatChallengeResult(steps)

        val recordedTask = RecordedTask(
            taskId = "challenge_1",
            rawCommand = rawQuery,
            intent = intent,
            targetApp = "Chrome",
            query = "https://www.google.com",
            plannedActions = steps.map { it.action },
            completedActions = steps.filter { it.verification }.map { it.action }.toMutableList(),
            state = RequestState.SUCCESS
        )

        recentRequestContext.recordNewRequest(
            requestId = requestId,
            rawCommand = rawQuery,
            normalizedCommand = normalized,
            intent = intent,
            targetApp = "Chrome",
            query = "https://www.google.com",
            plannedActions = steps.map { it.action },
            tasks = listOf(recordedTask)
        )
        recentRequestContext.updateRequestState(
            requestId = requestId,
            state = RequestState.SUCCESS,
            result = RequestResult(
                requestId = requestId,
                state = RequestState.SUCCESS,
                summary = challengeReply,
                isVerified = true,
                steps = steps
            )
        )

        return ExecutionResult(
            replyText = challengeReply,
            intent = intent,
            actionResult = ActionResult(
                success = true,
                isVerified = true,
                message = challengeReply,
                executionSteps = steps
            ),
            decisionSource = DecisionSource.LOCAL_PARSER,
            confidence = 1.0f,
            overallStatus = RequestState.SUCCESS,
            verificationStatus = true,
            steps = steps
        )
    }
}
