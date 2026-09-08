package com.example.brain

import com.example.models.DetailedExecutionResult
import com.example.models.ExecutionStatus
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.models.PendingConfirmation
import com.example.models.PendingConfirmationType
import com.example.models.RequestResult
import com.example.models.RequestState
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * ConversationContext maintains short-term dialogue context:
 * - lastUserMessage
 * - lastAssistantMessage
 * - currentRequest
 * - lastRequest
 * - recentRequests
 * - lastExecutionResult
 * - lastFailure
 * - pendingConfirmation
 * - pendingClarification
 *
 * This provides the memory and state needed to interpret short follow-ups,
 * confirmations ("haan"), denials ("nahi"), retries, and context questions.
 */
class ConversationContext(
    val recentRequestContext: RecentRequestContext = RecentRequestContext()
) {
    var lastUserMessage: String? = null
        private set

    var lastAssistantMessage: String? = null
        private set

    var currentRequest: RecordedRequest? = null
        private set

    var lastExecutionResult: DetailedExecutionResult? = null
        private set

    var lastFailure: DetailedExecutionResult? = null
        private set

    var pendingConfirmation: PendingConfirmation? = null
        private set

    var pendingClarification: String? = null
        private set

    fun recordUserMessage(message: String) {
        lastUserMessage = message
    }

    fun recordUserTurn(message: String, intent: ParsedIntent? = null, category: com.example.models.MessageCategory? = null) {
        lastUserMessage = message
    }

    fun recordAssistantResponse(text: String, confirmation: PendingConfirmation? = null) {
        lastAssistantMessage = text
        pendingConfirmation = confirmation
    }

    fun recordAssistantTurn(text: String, category: com.example.models.MessageCategory? = null, confirmation: PendingConfirmation? = null) {
        lastAssistantMessage = text
        if (confirmation != null) {
            pendingConfirmation = confirmation
        }
    }

    fun setPendingConfirmation(confirmation: PendingConfirmation?) {
        pendingConfirmation = confirmation
    }

    fun clearPendingConfirmation(): PendingConfirmation? {
        val prev = pendingConfirmation
        pendingConfirmation = null
        return prev
    }

    fun setPendingClarification(clarification: String?) {
        pendingClarification = clarification
    }

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
        val req = recentRequestContext.recordNewRequest(
            requestId = requestId,
            rawCommand = rawCommand,
            normalizedCommand = normalizedCommand,
            intent = intent,
            targetApp = targetApp,
            query = query,
            plannedActions = plannedActions,
            tasks = tasks,
            metaInstruction = metaInstruction
        )
        currentRequest = req
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
        recentRequestContext.updateRequestState(
            requestId = requestId,
            state = state,
            result = result,
            completedAction = completedAction,
            failedAction = failedAction,
            isSearchOnlyStarted = isSearchOnlyStarted,
            wasMisinterpreted = wasMisinterpreted
        )
    }

    fun recordExecutionResult(detailedResult: DetailedExecutionResult) {
        lastExecutionResult = detailedResult
        if (detailedResult.finalStatus == ExecutionStatus.FAILED ||
            detailedResult.finalStatus == ExecutionStatus.PARTIAL_SUCCESS ||
            detailedResult.currentState == RequestState.FAILED ||
            detailedResult.currentState == RequestState.PARTIAL_SUCCESS
        ) {
            lastFailure = detailedResult
        }
    }

    fun getLastRequest(): RecordedRequest? = recentRequestContext.getLastRequest()

    fun getRecentRequests(count: Int = 5): List<RecordedRequest> = recentRequestContext.getRecentRequests(count)

    fun getRequestByRecallIndex(index: Int): RecordedRequest? = recentRequestContext.getRequestByRecallIndex(index)

    fun formatRecallMessage(): String = recentRequestContext.formatRecallMessage()

    fun formatExecutionReportForIndex(index: Int): String = recentRequestContext.formatExecutionReportForIndex(index)

    fun formatLastExecutionReport(): String = recentRequestContext.formatLastExecutionReport()

    fun formatLastFailureExplanation(): String = recentRequestContext.formatLastFailureExplanation()

    fun markLastRequestFailed(reason: String = "User feedback: operation not completed") {
        recentRequestContext.markLastRequestFailed(reason)
        val lastReq = getLastRequest()
        if (lastReq != null) {
            val failedResult = DetailedExecutionResult(
                requestId = lastReq.requestId,
                userCommand = lastReq.rawUserCommand,
                intent = lastReq.intent,
                target = lastReq.targetApp,
                currentState = RequestState.FAILED,
                finalStatus = ExecutionStatus.FAILED,
                failureReason = reason,
                summary = "काम पूरा नहीं हुआ"
            )
            lastFailure = failedResult
            lastExecutionResult = failedResult
        }
    }

    fun markLastRequestMisinterpreted(correction: String) {
        recentRequestContext.markLastRequestMisinterpreted(correction)
        val lastReq = getLastRequest()
        if (lastReq != null) {
            val misResult = DetailedExecutionResult(
                requestId = lastReq.requestId,
                userCommand = lastReq.rawUserCommand,
                intent = lastReq.intent,
                target = lastReq.targetApp,
                currentState = RequestState.FAILED,
                finalStatus = ExecutionStatus.FAILED,
                wasMisinterpreted = true,
                failureReason = "Intent misclassified: $correction",
                summary = "कमांड को गलत समझा गया था"
            )
            lastFailure = misResult
            lastExecutionResult = misResult
        }
    }

    fun clear() {
        lastUserMessage = null
        lastAssistantMessage = null
        currentRequest = null
        lastExecutionResult = null
        lastFailure = null
        pendingConfirmation = null
        pendingClarification = null
        recentRequestContext.clearHistory()
    }
}
