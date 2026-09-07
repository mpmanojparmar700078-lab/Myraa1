package com.example.brain

import com.example.models.ActionResult
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
    val timestamp: Long = System.currentTimeMillis()
)

class RecentRequestContext {
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
        tasks: List<RecordedTask> = emptyList()
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
            tasks = tasks
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
        failedAction: String? = null
    ) {
        val req = history.find { it.requestId == requestId } ?: return
        req.currentState = state
        if (result != null) {
            req.finalResult = result
        }
        completedAction?.let { req.completedActions.add(it) }
        failedAction?.let { req.failedActions.add(it) }
    }

    fun getLastRequest(): RecordedRequest? {
        return history.lastOrNull()
    }

    fun getRecentRequests(count: Int = 5): List<RecordedRequest> {
        return history.toList().takeLast(count).reversed()
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

        val result = last.finalResult
        return when (last.currentState) {
            RequestState.SUCCESS -> {
                "काम पूरा हुआ: ${result?.summary ?: "अनुरोध सफलतापूर्वक पूरा और सत्यापित हुआ।"}"
            }
            RequestState.PARTIAL_SUCCESS -> {
                val target = last.targetApp?.replaceFirstChar { it.uppercase() } ?: "ऐप"
                val query = last.query?.let { " '$it'" } ?: ""
                "मैंने $target खोला और$query खोजने की कोशिश की, लेकिन अंतिम परिणाम (playback/page) verify नहीं हो पाया।"
            }
            RequestState.FAILED -> {
                "काम पूरा नहीं हो सका: ${result?.failureReason ?: "स्क्रीन पर आवश्यक एलिमेंट नहीं मिला या परमिशन उपलब्ध नहीं थी।"}"
            }
            RequestState.CANCELLED -> {
                "पिछला अनुरोध रद्द कर दिया गया था।"
            }
            RequestState.EXECUTING, RequestState.WAITING_FOR_SCREEN, RequestState.VERIFYING -> {
                "पिछला अनुरोध अभी चल रहा है (${last.currentState})।"
            }
            else -> {
                "पिछली कमांड: \"${last.rawUserCommand}\" (स्थिति: ${last.currentState})"
            }
        }
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
