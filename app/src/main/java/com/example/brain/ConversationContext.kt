package com.example.brain

import com.example.models.ConversationMessage
import com.example.models.DetailedExecutionResult
import com.example.models.ExecutionStatus
import com.example.models.IntentType
import com.example.models.MessageRole
import com.example.models.ParsedIntent
import com.example.models.PendingConfirmation
import com.example.models.PendingConfirmationType
import com.example.models.RequestResult
import com.example.models.RequestState
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * ConversationContext maintains short-term dialogue context:
 * - conversationHistory: Complete record of ALL user and assistant messages (chat, questions, commands)
 * - recentRequestContext: Commands & executable action history ONLY
 * - executionHistory: Actual execution attempts and results
 *
 * This separation is mandatory so questions like "mene kya pucha" search
 * ConversationHistory instead of Command History.
 */
class ConversationContext(
    val recentRequestContext: RecentRequestContext = RecentRequestContext()
) {
    val conversationHistory = ConcurrentLinkedDeque<ConversationMessage>()
    val executionHistory = ConcurrentLinkedDeque<DetailedExecutionResult>()

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

    fun recordUserMessage(
        message: String,
        normalized: String = TextNormalizer.normalize(message),
        intent: IntentType = IntentType.UNKNOWN
    ): ConversationMessage {
        lastUserMessage = message
        val convMsg = ConversationMessage(
            role = MessageRole.USER,
            rawText = message,
            normalizedText = normalized,
            intent = intent
        )
        conversationHistory.addLast(convMsg)
        while (conversationHistory.size > 50) {
            conversationHistory.removeFirst()
        }
        return convMsg
    }

    fun recordUserTurn(message: String, intent: ParsedIntent? = null, category: com.example.models.MessageCategory? = null) {
        lastUserMessage = message
        val last = conversationHistory.lastOrNull { it.role == MessageRole.USER }
        val intType = intent?.type ?: IntentType.UNKNOWN
        if (last != null && last.rawText == message) {
            if (intType != IntentType.UNKNOWN) {
                last.intent = intType
            }
            return
        }
        val normalized = TextNormalizer.normalize(message)
        val convMsg = ConversationMessage(
            role = MessageRole.USER,
            rawText = message,
            normalizedText = normalized,
            intent = intType
        )
        conversationHistory.addLast(convMsg)
        while (conversationHistory.size > 50) {
            conversationHistory.removeFirst()
        }
    }

    fun recordAssistantResponse(
        text: String,
        confirmation: PendingConfirmation? = null,
        intent: IntentType = IntentType.UNKNOWN,
        relatedRequestId: Long? = null
    ): ConversationMessage {
        lastAssistantMessage = text
        pendingConfirmation = confirmation
        val last = conversationHistory.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (last != null && last.rawText == text && (System.currentTimeMillis() - last.timestamp) < 3000) {
            if (intent != IntentType.UNKNOWN) last.intent = intent
            if (relatedRequestId != null) last.relatedRequestId = relatedRequestId
            return last
        }
        val convMsg = ConversationMessage(
            role = MessageRole.ASSISTANT,
            rawText = text,
            normalizedText = text,
            intent = intent,
            response = text,
            relatedRequestId = relatedRequestId
        )
        conversationHistory.addLast(convMsg)
        while (conversationHistory.size > 50) {
            conversationHistory.removeFirst()
        }
        return convMsg
    }

    fun recordAssistantTurn(text: String, category: com.example.models.MessageCategory? = null, confirmation: PendingConfirmation? = null) {
        recordAssistantResponse(text = text, confirmation = confirmation)
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
        executionHistory.addLast(detailedResult)
        while (executionHistory.size > 30) {
            executionHistory.removeFirst()
        }
        if (detailedResult.finalStatus == ExecutionStatus.FAILED ||
            detailedResult.finalStatus == ExecutionStatus.PARTIAL_SUCCESS ||
            detailedResult.currentState == RequestState.FAILED ||
            detailedResult.currentState == RequestState.PARTIAL_SUCCESS
        ) {
            lastFailure = detailedResult
        }
    }

    fun hasUserMessages(skipLast: Boolean = true): Boolean {
        val userMessages = conversationHistory.filter { it.role == MessageRole.USER }
        return if (skipLast) userMessages.size >= 2 else userMessages.isNotEmpty()
    }

    /**
     * Retrieves previous user message from ConversationHistory.
     * When current user message has already been recorded, skipLast=true skips it.
     */
    fun getPreviousUserMessage(skipLast: Boolean = true): ConversationMessage? {
        val userMessages = conversationHistory.filter { it.role == MessageRole.USER }
        return if (skipLast) {
            if (userMessages.size >= 2) userMessages[userMessages.size - 2] else null
        } else {
            userMessages.lastOrNull()
        }
    }

    /**
     * Retrieves the latest previous user question.
     * Prioritizes messages with question marks, question words, or question intents.
     */
    fun getPreviousUserQuestion(skipLast: Boolean = true): ConversationMessage? {
        val userMessages = conversationHistory.filter { it.role == MessageRole.USER }
        val pool = if (skipLast && userMessages.isNotEmpty()) userMessages.dropLast(1) else userMessages
        return pool.findLast { msg ->
            isQuestion(msg.rawText) ||
            msg.intent == IntentType.QUESTION ||
            msg.intent == IntentType.IDENTITY_QUESTION ||
            msg.intent == IntentType.API_KEY_STATUS_QUERY ||
            msg.intent == IntentType.CONTEXT_QUERY ||
            msg.intent == IntentType.WHY_QUERY ||
            msg.intent == IntentType.EXECUTION_STATUS_QUERY
        } ?: pool.lastOrNull()
    }

    fun getPreviousAssistantMessage(): ConversationMessage? {
        return conversationHistory.filter { it.role == MessageRole.ASSISTANT }.lastOrNull()
    }

    private fun isQuestion(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.contains("?") || lower.contains("？")) return true
        val questionKeywords = listOf(
            "kya", "kyu", "kyo", "kaise", "kab", "kahan", "kidhar", "kitna", "kitne",
            "kisko", "kiska", "lgi ya nhi", "lagi ya nahi", "hai ya nahi", "hai ya nhi",
            "lagi hai", "lgi hai", "status", "who", "what", "where", "when", "why", "how",
            "kon ho", "kaun ho", "koun ho", "kon hai", "kaun hai", "koun hai",
            "who are", "who is", "who are you",
            "क्या", "क्यों", "कैसे", "कहाँ", "कब", "कितना", "कौन"
        )
        return questionKeywords.any { lower.contains(it) }
    }

    fun formatPreviousUserQuestionResponse(): String {
        val prev = getPreviousUserQuestion(skipLast = true) ?: getPreviousUserMessage(skipLast = true)
        return if (prev != null) {
            "आपने पूछा था: \"${prev.rawText}\""
        } else {
            "हाल ही में बातचीत में कोई पिछला सवाल दर्ज नहीं हुआ है।"
        }
    }

    fun formatPreviousUserMessageResponse(): String {
        val prev = getPreviousUserMessage(skipLast = true)
        return if (prev != null) {
            val verb = if (isQuestion(prev.rawText) || prev.intent == IntentType.QUESTION || prev.intent == IntentType.IDENTITY_QUESTION) "पूछा" else "कहा"
            "आपने अभी $verb था: \"${prev.rawText}\""
        } else {
            "हाल ही में बातचीत में कोई पिछला संदेश दर्ज नहीं हुआ है।"
        }
    }

    fun formatPreviousAssistantMessageResponse(): String {
        val prev = getPreviousAssistantMessage()
        return if (prev != null) {
            "मैंने अभी कहा था: \"${prev.rawText}\""
        } else {
            "हाल ही में मेरा कोई पिछला जवाब दर्ज नहीं हुआ है।"
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
            executionHistory.addLast(failedResult)
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
            executionHistory.addLast(misResult)
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
        conversationHistory.clear()
        executionHistory.clear()
        recentRequestContext.clearHistory()
    }
}
