package com.example.brain

import com.example.models.ActionResult
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.models.RequestResult
import com.example.models.RequestState
import java.util.concurrent.ConcurrentLinkedDeque

data class RecordedTask(
    val taskId: String,
    val rawCommand: String,
    val intent: ParsedIntent,
    val targetApp: String?,
    val query: String?,
    val plannedActions: List<String>,
    val completedActions: MutableList<String> = mutableListOf(),
    val failedActions: MutableList<String> = mutableListOf(),
    var state: RequestState = RequestState.RECEIVED,
    var finalResult: RequestResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class RecordedRequest(
    val requestId: Long,
    val rawUserCommand: String,
    val normalizedCommand: String,
    val intent: ParsedIntent,
    val targetApp: String?,
    val query: String?,
    val plannedActions: List<String>,
    val completedActions: MutableList<String> = mutableListOf(),
    val failedActions: MutableList<String> = mutableListOf(),
    var currentState: RequestState = RequestState.RECEIVED,
    var finalResult: RequestResult? = null,
    val tasks: List<RecordedTask> = emptyList(),
    var wasMisinterpreted: Boolean = false,
    var isSearchOnlyStarted: Boolean = false,
    var metaInstruction: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class RecentRequestContext(
    private val resultReporter: ResultReporter = ResultReporter()
) {
    private val history = ConcurrentLinkedDeque<RecordedRequest>()
    private val maxHistorySize = 25

    fun recordNewRequest(
        requestId: Long,
        rawCommand: String,
        normalizedCommand: String,
        intent: ParsedIntent,
        targetApp: String?,
        query: String?,
        plannedActions: List<String>,
        tasks: List<RecordedTask> = emptyList(),
        metaInstruction: String? = null
    ): RecordedRequest {
        val req = RecordedRequest(
            requestId = requestId,
            rawUserCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            intent = intent,
            targetApp = targetApp,
            query = query,
            plannedActions = plannedActions,
            currentState = RequestState.RECEIVED,
            tasks = tasks,
            metaInstruction = metaInstruction ?: intent.metaInstruction
        )
        history.addLast(req)
        while (history.size > maxHistorySize) {
            history.removeFirst()
        }
        return req
    }

    fun updateRequestState(
        requestId: Long,
        state: RequestState,
        result: RequestResult? = null,
        completedAction: String? = null,
        failedAction: String? = null,
        isSearchOnlyStarted: Boolean = false,
        wasMisinterpreted: Boolean = false
    ) {
        val req = history.find { it.requestId == requestId } ?: return
        req.currentState = state
        if (isSearchOnlyStarted) req.isSearchOnlyStarted = true
        if (wasMisinterpreted) req.wasMisinterpreted = true
        if (result != null) {
            req.finalResult = result
        }
        completedAction?.let { req.completedActions.add(it) }
        failedAction?.let { req.failedActions.add(it) }
    }

    fun markLastRequestFailed(reason: String = "User feedback: operation not completed") {
        val last = getLastRequest() ?: return
        last.currentState = RequestState.FAILED
        val prevResult = last.finalResult
        last.finalResult = (prevResult ?: RequestResult(
            requestId = last.requestId,
            state = RequestState.FAILED,
            summary = "काम पूरा नहीं हुआ",
            failureReason = reason
        )).copy(state = RequestState.FAILED, failureReason = reason)
    }

    fun markLastRequestMisinterpreted(correction: String) {
        val last = getLastRequest() ?: return
        last.wasMisinterpreted = true
        last.currentState = RequestState.FAILED
        val prevResult = last.finalResult
        last.finalResult = (prevResult ?: RequestResult(
            requestId = last.requestId,
            state = RequestState.FAILED,
            summary = "कमांड को गलत समझा गया था",
            wasMisinterpreted = true,
            failureReason = "Intent misclassified: $correction"
        )).copy(
            state = RequestState.FAILED,
            wasMisinterpreted = true,
            failureReason = "Intent misclassified: $correction"
        )
    }

    fun getLastRequest(): RecordedRequest? {
        return history.lastOrNull()
    }

    fun getRecentRequests(count: Int = 5): List<RecordedRequest> {
        return history.toList().takeLast(count).reversed()
    }

    fun getRequestByRecallIndex(index: Int): RecordedRequest? {
        val recents = getRecentRequests(10)
        return if (index in 1..recents.size) recents[index - 1] else null
    }

    fun formatRecallMessage(): String {
        val last = getLastRequest() ?: return "हाल ही में कोई कमांड रिकॉर्ड नहीं हुआ है।"
        val recents = getRecentRequests(3)
        return if (recents.size == 1) {
            "आपने कहा था: \"${last.rawUserCommand}\""
        } else {
            val sb = StringBuilder("आपने हाल ही में ये कमांड दिए थे:\n")
            recents.forEachIndexed { i, req ->
                sb.append("${i + 1}. \"${req.rawUserCommand}\"\n")
            }
            sb.toString().trimEnd()
        }
    }

    fun formatExecutionReportForIndex(index: Int): String {
        val req = getRequestByRecallIndex(index)
            ?: return "अनुरोध #$index इतिहास में नहीं मिला।"
        return resultReporter.formatReportForRequest(
            rawCommand = req.rawUserCommand,
            intent = req.intent,
            state = req.currentState,
            result = req.finalResult,
            targetApp = req.targetApp,
            query = req.query,
            wasMisinterpreted = req.wasMisinterpreted,
            isSearchOnlyStarted = req.isSearchOnlyStarted,
            metaInstruction = req.metaInstruction,
            itemIndex = index
        )
    }

    fun formatLastExecutionReport(): String {
        val last = getLastRequest() ?: return "हाल ही में कोई एक्शन निष्पादित नहीं किया गया है।"

        if (last.tasks.size > 1) {
            val sb = StringBuilder("आपके पिछले अनुरोध में ${last.tasks.size} काम थे:\n")
            last.tasks.forEachIndexed { i, task ->
                val num = i + 1
                val app = task.targetApp?.replaceFirstChar { it.uppercase() } ?: "टास्क"
                val q = task.query?.let { " ('$it')" } ?: ""
                when (task.state) {
                    RequestState.SUCCESS -> sb.append("$num. $app$q: काम पूरा और सत्यापित हुआ।\n")
                    RequestState.PARTIAL_SUCCESS -> sb.append("$num. $app$q: ऐप खुला, लेकिन अंतिम सत्यापन (playback/page) अधूरा रहा।\n")
                    RequestState.FAILED -> sb.append("$num. $app$q: पूरा नहीं हो सका (${task.finalResult?.failureReason ?: "असफल"})\n")
                    RequestState.CANCELLED -> sb.append("$num. $app$q: रद्द किया गया।\n")
                    else -> sb.append("$num. $app$q: स्थिति - ${task.state}\n")
                }
            }
            return sb.toString().trimEnd()
        }

        return resultReporter.formatReportForRequest(
            rawCommand = last.rawUserCommand,
            intent = last.intent,
            state = last.currentState,
            result = last.finalResult,
            targetApp = last.targetApp,
            query = last.query,
            wasMisinterpreted = last.wasMisinterpreted,
            isSearchOnlyStarted = last.isSearchOnlyStarted,
            metaInstruction = last.metaInstruction
        )
    }

    fun formatLastFailureExplanation(): String {
        val last = getLastRequest() ?: return "कोई पिछला असफल अनुरोध नहीं मिला।"
        val failedTask = last.tasks.firstOrNull { it.state == RequestState.FAILED || it.state == RequestState.PARTIAL_SUCCESS }
        val reason = failedTask?.finalResult?.failureReason
            ?: last.finalResult?.failureReason
            ?: "एक्सेसिबिलिटी स्क्रीन पर लक्ष्य वीडियो या प्लेयर नहीं मिला, या स्क्रीन लोड होने में देरी हुई।"
        return "पिछला काम पूरा न होने का कारण: $reason"
    }

    fun clearHistory() {
        history.clear()
    }
}
