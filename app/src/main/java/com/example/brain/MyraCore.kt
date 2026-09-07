package com.example.brain

import android.content.Context
import android.util.Log
import com.example.accessibility.ScreenControlEngine
import com.example.data.MemoryRepository
import com.example.models.ActionResult
import com.example.models.ActionStep
import com.example.models.DecisionSource
import com.example.models.DiagnosticLog
import com.example.models.ExecutionDecision
import com.example.models.InstalledAppInfo
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.platform.android.AppLauncher
import com.example.services.GeminiService
import com.example.services.LocalCommandParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class ExecutionResult(
    val replyText: String,
    val intent: ParsedIntent,
    val actionResult: ActionResult,
    val decisionSource: DecisionSource,
    val confidence: Float,
    val wasCancelled: Boolean = false
)

class MyraCore(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val appLauncher: AppLauncher,
    private val localCommandParser: LocalCommandParser,
    private val geminiService: GeminiService,
    private val screenControlEngine: ScreenControlEngine = ScreenControlEngine()
) {

    companion object {
        private const val TAG = "MyraCore"
        private const val MAX_LOOP_RETRIES = 3
    }

    private val confidenceEngine = ConfidenceEngine()
    private val learningEngine = LearningEngine(memoryRepository, confidenceEngine)

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

            // 3. Execute according to decision
            val result = if (confidenceEngine.isConfidentForLocalExecution(decision.confidence) || decision.source != DecisionSource.GEMINI_FALLBACK) {
                // Execute Locally! (NO GEMINI CALL)
                logDiagnostic("LOCAL_PLANNER", "Executing LOCALLY with no Gemini call needed.")
                executeIntentLocally(decision.intent, rawQuery, installedApps)
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
                    ActionResult(success = true, message = aiResponse)
                }
            }

            // 4. Update anti-loop counters
            if (result.success) {
                commandFailureTracker.remove(normalized)
            } else {
                commandFailureTracker[normalized] = previousFailures + 1
            }

            // 5. Verification & Learning Loop
            val finalReply = result.message.ifBlank { decision.intent.responseText ?: "एक्शन पूरा किया गया।" }
            logDiagnostic("VERIFIER", "Result success=${result.success}: \"${finalReply.take(40)}\"")

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
                confidence = decision.confidence
            )

        } catch (e: CancellationException) {
            logDiagnostic("MYRA_CORE", "Request #$requestId was cancelled during execution.")
            throw e
        } catch (e: Exception) {
            logDiagnostic("MYRA_CORE", "Error executing request #$requestId: ${e.localizedMessage}")
            val errorMsg = "कमांड पूरी करने में समस्या आई: ${e.localizedMessage}"
            return ExecutionResult(
                replyText = errorMsg,
                intent = ParsedIntent(type = IntentType.UNKNOWN),
                actionResult = ActionResult(success = false, message = errorMsg, error = e.localizedMessage),
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
        // Step 2a: Check Local Command Parser
        val localResult = localCommandParser.parse(rawQuery)
        if (localResult.handled && localResult.intent != null) {
            val conf = localResult.confidence
            logDiagnostic("LOCAL_PARSER", "Intent matched: ${localResult.intent.type} (confidence: $conf)")
            return ExecutionDecision(
                source = DecisionSource.LOCAL_PARSER,
                intent = localResult.intent,
                confidence = conf,
                explanation = "Matched local grammar & semantic patterns"
            )
        }

        // Step 2b: Check Learned Skills
        val skills = memoryRepository.getEnabledSkills()
        for (skill in skills) {
            val isMatched = try {
                val triggers = JSONArray(skill.triggerPatternsJson)
                var found = false
                for (i in 0 until triggers.length()) {
                    val t = triggers.getString(i).lowercase()
                    if (normalized == t || normalized.contains(t) || t.contains(normalized)) {
                        found = true
                        break
                    }
                }
                found
            } catch (e: Exception) {
                false
            }

            if (isMatched) {
                val conf = confidenceEngine.calculateSkillConfidence(skill)
                logDiagnostic("SKILL", "Matched Learned Skill: '${skill.skillName}' (confidence: $conf)")
                return ExecutionDecision(
                    source = DecisionSource.LEARNED_SKILL,
                    intent = ParsedIntent(
                        type = IntentType.EXECUTE_SKILL,
                        target = skill.requiredApp,
                        skillName = skill.skillName,
                        confidence = conf
                    ),
                    confidence = conf,
                    explanation = "Matched learned skill '${skill.skillName}' v${skill.version}",
                    matchedSkillId = skill.id
                )
            }
        }

        // Step 2c: Check Experience Memory
        val exactExp = memoryRepository.findExactExperience(normalized)
        if (exactExp != null && exactExp.resultSuccess) {
            val conf = confidenceEngine.calculateExperienceConfidence(exactExp)
            if (conf >= 0.70f) {
                val parsedType = try {
                    IntentType.valueOf(exactExp.intentType)
                } catch (e: Exception) {
                    IntentType.OPEN_APP
                }
                logDiagnostic("MEMORY", "Matched past experience with confidence $conf (successes: ${exactExp.successCount})")
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
        installedApps: List<InstalledAppInfo>
    ): ActionResult {
        return when (intent.type) {
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

            IntentType.YOUTUBE_SEARCH, IntentType.YOUTUBE_SEARCH_AND_PLAY -> {
                val q = intent.query ?: rawQuery
                appLauncher.searchYouTube(q)
            }

            IntentType.OPEN_URL -> {
                val url = intent.target ?: rawQuery
                appLauncher.openUrl(url)
            }

            IntentType.GO_BACK -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "BACK"))
                ActionResult(success = true, message = "पीछे जाया गया।")
            }

            IntentType.GO_HOME -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "HOME"))
                ActionResult(success = true, message = "होम स्क्रीन पर जाया गया।")
            }

            IntentType.SCROLL_DOWN -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "SCROLL_DOWN"))
                ActionResult(success = true, message = "नीचे स्क्रॉल किया गया।")
            }

            IntentType.SCROLL_UP -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "SCROLL_UP"))
                ActionResult(success = true, message = "ऊपर स्क्रॉल किया गया।")
            }

            IntentType.PLAY -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "CLICK", target = "play"))
                ActionResult(success = true, message = "प्ले किया जा रहा है।")
            }

            IntentType.PAUSE -> {
                screenControlEngine.executeStep(ActionStep(stepId = 1, actionType = "CLICK", target = "pause"))
                ActionResult(success = true, message = "पॉज़ किया गया।")
            }

            IntentType.READ_SCREEN -> {
                val snapshot = screenControlEngine.getCurrentSnapshot()
                val visibleTexts = snapshot.visibleNodes.mapNotNull { it.text ?: it.contentDescription }.distinct().take(10)
                val summary = if (visibleTexts.isNotEmpty()) {
                    "स्क्रीन पर दिखाई दे रहा है: " + visibleTexts.joinToString(", ")
                } else {
                    "स्क्रीन पर कोई टेक्स्ट नहीं मिला (एक्सेसिबिलिटी परमिशन चेक करें)।"
                }
                ActionResult(success = true, message = summary)
            }

            IntentType.EXECUTE_SKILL -> {
                if (intent.target != null) {
                    appLauncher.launchAppByPackage(intent.target)
                }
                ActionResult(success = true, message = "स्किल '${intent.skillName}' निष्पादित किया गया।")
            }

            IntentType.CANCEL_REQUEST -> {
                cancelActiveRequest()
                ActionResult(success = true, message = "कमांड रद्द की गई।")
            }

            IntentType.CLEAR_CHAT -> {
                ActionResult(success = true, message = "चैट साफ़ की गई।")
            }

            else -> {
                val reply = intent.responseText ?: "कमांड पूरी की गई।"
                ActionResult(success = true, message = reply)
            }
        }
    }
}
