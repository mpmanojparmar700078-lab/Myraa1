package com.example.brain

import com.example.models.ApiKeyStatus
import com.example.models.DetailedExecutionResult
import com.example.models.ExecutionStatus
import com.example.models.ExecutionStep
import com.example.models.GeneratedResponse
import com.example.models.IntentType
import com.example.models.MessageCategory
import com.example.models.ParsedIntent
import com.example.models.PendingConfirmation
import com.example.models.PendingConfirmationType
import com.example.models.RequestState
import com.example.models.ResponseType

/**
 * Centralized MyraResponseEngine:
 *
 * Enforces the Truthful Response Rule:
 * Myra must NEVER say "काम पूरा हुआ" unless the requested outcome has actually been verified.
 *
 * Coordinates message category, parsed intent, conversation context, and execution results
 * into single, consistent, truth-anchored user responses.
 */
class MyraResponseEngine(
    private val resultReporter: ResultReporter = ResultReporter(),
    val apiKeyStatusProvider: (() -> ApiKeyStatus)? = null
) {

    fun generateResponse(
        category: MessageCategory,
        intent: ParsedIntent,
        context: ConversationContext,
        executionResult: DetailedExecutionResult? = null,
        confidence: Float = 1.0f,
        requestId: Long = System.currentTimeMillis()
    ): GeneratedResponse {
        return when (category) {
            MessageCategory.GREETING -> generateGreetingResponse(requestId)
            MessageCategory.GENERAL_CONVERSATION -> generateConversationResponse(intent, requestId)
            MessageCategory.QUESTION -> generateQuestionResponse(intent, requestId)
            MessageCategory.API_KEY_STATUS_QUERY -> {
                val status = apiKeyStatusProvider?.invoke() ?: ApiKeyStatus.NOT_CONFIGURED
                GeneratedResponse(
                    text = generateApiKeyStatusResponse(status),
                    responseType = ResponseType.API_KEY_REPORT,
                    requestId = requestId
                )
            }
            MessageCategory.WHY_QUESTION -> {
                GeneratedResponse(
                    text = generateWhyResponse(context, context.recentRequestContext),
                    responseType = ResponseType.CONVERSATION,
                    requestId = requestId
                )
            }
            MessageCategory.META_CONVERSATION -> {
                GeneratedResponse(
                    text = generateMetaConversationResponse(context),
                    responseType = ResponseType.CONVERSATION,
                    requestId = requestId
                )
            }
            MessageCategory.CONTEXT_QUESTION -> generateContextReportResponse(intent, context, requestId)
            MessageCategory.EXECUTION_STATUS -> generateExecutionReportResponse(intent, context, executionResult, requestId)
            MessageCategory.FAILURE_FEEDBACK -> generateFailureFeedbackResponse(context, requestId)
            MessageCategory.RETRY_REQUEST -> generateRetryResponse(context, requestId)
            MessageCategory.CANCEL_REQUEST -> generateCancelResponse(requestId)
            MessageCategory.CONFIRMATION -> generateConfirmationResponse(context, requestId)
            MessageCategory.DENIAL -> generateDenialResponse(context, requestId)
            MessageCategory.CORRECTION -> generateCorrectionResponse(intent, context, requestId)
            MessageCategory.META_INSTRUCTION -> generateMetaInstructionResponse(intent, requestId)
            MessageCategory.MEMORY_QUERY -> generateMemoryQueryResponse(requestId)
            MessageCategory.PREFERENCE -> generatePreferenceResponse(intent, requestId)
            MessageCategory.NEW_COMMAND, MessageCategory.FOLLOW_UP -> {
                if (executionResult != null) {
                    generateActionResultResponse(executionResult, requestId)
                } else {
                    GeneratedResponse(
                        text = intent.responseText ?: "एक्शन शुरू किया जा रहा है...",
                        responseType = ResponseType.ACTION_PROGRESS,
                        requestId = requestId
                    )
                }
            }
            MessageCategory.UNKNOWN -> {
                val reply = intent.responseText
                    ?: "माफ़ कीजिए, मैं समझ नहीं पाई। कृपया दोबारा स्पष्ट शब्दों में बताएं।"
                GeneratedResponse(
                    text = reply,
                    responseType = ResponseType.TEXT,
                    requestId = requestId
                )
            }
        }
    }

    private fun generateGreetingResponse(requestId: Long): GeneratedResponse {
        return GeneratedResponse(
            text = "नमस्ते! मैं यहाँ हूँ। बताइए, क्या करना है?",
            responseType = ResponseType.CONVERSATION,
            requestId = requestId
        )
    }

    private fun generateConversationResponse(intent: ParsedIntent, requestId: Long): GeneratedResponse {
        val query = intent.query?.lowercase() ?: intent.responseText?.lowercase() ?: ""
        val text = when {
            query.contains("kaise ho") || query.contains("kaisi ho") || query.contains("how are you") || query.contains("हाल") ->
                "मैं बिल्कुल ठीक हूँ! बताइए, आज क्या करना है?"
            query.contains("theek hai") || query.contains("achha") || query.contains("accha") || query.contains("ok") || query.contains("samajh gaya") ->
                "जी, बताइए आगे क्या करना है।"
            query.contains("thanks") || query.contains("thank you") || query.contains("shukriya") || query.contains("dhanyawad") || query.contains("धन्यवाद") ->
                "आपका स्वागत है! कोई और काम हो तो बताइए।"
            query.contains("wah") || query.contains("badhiya") || query.contains("shabash") ->
                "धन्यवाद! आगे बताइए मैं क्या मदद करूँ।"
            else ->
                "जी, मैं सुन रही हूँ। बताइए क्या करना है।"
        }
        return GeneratedResponse(
            text = text,
            responseType = ResponseType.CONVERSATION,
            requestId = requestId
        )
    }

    private fun generateQuestionResponse(intent: ParsedIntent, requestId: Long): GeneratedResponse {
        val query = intent.query?.lowercase() ?: intent.responseText?.lowercase() ?: ""
        val text = when {
            query.contains("who are you") || query.contains("kaun ho") || query.contains("कौन हो") || query.contains("naam kya") ->
                "मैं Myra हूँ, आपकी स्मार्ट पर्सनल AI असिस्टेंट। मैं आपके फ़ोन पर ऐप्स खोलने, वीडियो चलाने और विभिन्न कार्य करने में मदद कर सकती हूँ।"
            query.contains("kya kar sakti ho") || query.contains("what can you do") || query.contains("kya karti ho") ->
                "मैं आपकी डिवाइस पर ऐप्स खोलने, सर्च करने और टास्क ऑटोमेट करने में मदद कर सकती हूँ। बताइए क्या करूँ?"
            else ->
                "मैं इसे सही तरह समझ नहीं पाई। क्या आप अपने पिछले सवाल के बारे में पूछ रहे हैं, या कोई नया काम करना चाहते हैं?"
        }
        return GeneratedResponse(
            text = text,
            responseType = ResponseType.CONVERSATION,
            requestId = requestId
        )
    }

    private fun generateContextReportResponse(
        intent: ParsedIntent,
        context: ConversationContext,
        requestId: Long
    ): GeneratedResponse {
        val report = when (intent.type) {
            IntentType.CONTEXT_QUERY -> {
                if (intent.executionPreference == "assistant_response") {
                    context.formatPreviousAssistantMessageResponse()
                } else {
                    context.formatPreviousUserQuestionResponse()
                }
            }
            IntentType.RECALL_REQUEST -> {
                context.formatPreviousUserMessageResponse()
            }
            else -> {
                context.formatRecallMessage()
            }
        }
        return GeneratedResponse(
            text = report,
            responseType = ResponseType.CONTEXT_REPORT,
            requestId = requestId
        )
    }

    fun generateApiKeyStatusResponse(status: ApiKeyStatus): String {
        return when (status) {
            ApiKeyStatus.CONFIGURED -> "हाँ, API key configured है।"
            ApiKeyStatus.NOT_CONFIGURED -> "नहीं, API key configured नहीं है।"
            ApiKeyStatus.INVALID -> "API key अमान्य (invalid) है।"
            ApiKeyStatus.UNKNOWN -> "मैं अभी API key की configuration स्थिति verify नहीं कर पा रही हूँ।"
        }
    }

    fun generateWhyResponse(
        context: ConversationContext,
        recentRequestContext: RecentRequestContext? = null
    ): String {
        val lastAssistantMsg = context.getPreviousAssistantMessage()
        val lastAssistantText = lastAssistantMsg?.rawText ?: ""
        val lastFail = context.lastFailure ?: recentRequestContext?.getLastFailedRequest()

        // 1. If previous assistant response was about API key:
        if (lastAssistantText.contains("API key", ignoreCase = true) || lastAssistantMsg?.intent == IntentType.API_KEY_STATUS_QUERY) {
            val status = apiKeyStatusProvider?.invoke() ?: ApiKeyStatus.NOT_CONFIGURED
            return when (status) {
                ApiKeyStatus.NOT_CONFIGURED -> "क्योंकि सेटिंग्स में कोई Gemini API key दर्ज नहीं की गई है, इसलिए Myra ऑफ़लाइन/लोकल मोड में काम कर रही है।"
                ApiKeyStatus.CONFIGURED -> "क्योंकि API key कॉन्फ़िगर है और सिस्टम क्लाउड मॉडल से कनेक्ट होने के लिए तैयार है।"
                ApiKeyStatus.INVALID -> "क्योंकि दर्ज की गई API key मान्य नहीं पाई गई।"
                ApiKeyStatus.UNKNOWN -> "क्योंकि API key की स्थिति की पुष्टि नहीं हो सकी।"
            }
        }

        // 2. If previous assistant response was about context recall (e.g. "आपने अभी पूछा था..."):
        if (lastAssistantText.contains("आपने अभी पूछा था") || lastAssistantText.contains("आपने अभी बोला था") || lastAssistantMsg?.intent == IntentType.CONTEXT_QUERY) {
            return "क्योंकि आपने मुझसे पूछा था कि आपने पहले क्या कहा या पूछा था, इसलिए मैंने बातचीत के इतिहास से उसे दोहराया।"
        }

        // 3. If previous request or action failed:
        if (lastFail != null) {
            val reason = if (lastFail is DetailedExecutionResult) {
                lastFail.failureReason ?: lastFail.summary
            } else {
                recentRequestContext?.formatLastFailureExplanation() ?: "पिछला एक्शन पूरा नहीं हुआ"
            }
            return "क्योंकि पिछला काम पूरा नहीं हो सका: $reason"
        }

        // 4. If previous action was successful:
        val lastReq = context.getLastRequest()
        if (lastReq != null && (lastReq.currentState == RequestState.SUCCESS || lastReq.currentState == RequestState.REQUEST_SUCCEEDED)) {
            return "क्योंकि पिछला अनुरोध (\"${lastReq.rawUserCommand}\") सफलतापूर्वक पूरा हो गया था।"
        }

        // 5. Context-aware fallback - never generic capabilities!
        return "मैं आपके पिछले सवाल के जवाब के संबंध में बता रही थी। क्या आप किसी खास बात या स्टेप की वजह जानना चाहते हैं?"
    }

    fun generateMetaConversationResponse(context: ConversationContext): String {
        val lastUser = context.getPreviousUserQuestion(skipLast = true) ?: context.getPreviousUserMessage(skipLast = true)
        val lastAssistant = context.getPreviousAssistantMessage()

        val details = if (lastUser != null && lastAssistant != null) {
            " आपने पूछा था: \"${lastUser.rawText}\" और मेरा जवाब था: \"${lastAssistant.rawText}\"।"
        } else ""

        return "आप मुझसे पूछ रहे हैं कि आप क्या कह रहे हैं और मैं उसके जवाब में क्या कह रही हूँ। अभी आपने मेरी प्रतिक्रिया पर सवाल किया है क्योंकि आपको लग रहा है कि मेरा जवाब आपके सवाल से मेल नहीं खा रहा।$details बताइए, मैं इसे आपके लिए कैसे स्पष्ट करूँ?"
    }

    private fun generateExecutionReportResponse(
        intent: ParsedIntent,
        context: ConversationContext,
        executionResult: DetailedExecutionResult?,
        requestId: Long
    ): GeneratedResponse {
        val report = if (intent.targetIndex != null) {
            context.formatExecutionReportForIndex(intent.targetIndex)
        } else if (executionResult != null) {
            formatDetailedResultText(executionResult)
        } else {
            context.formatLastExecutionReport()
        }
        return GeneratedResponse(
            text = report,
            responseType = ResponseType.EXECUTION_REPORT,
            requestId = requestId,
            executionStatus = executionResult?.finalStatus
        )
    }

    private fun generateFailureFeedbackResponse(
        context: ConversationContext,
        requestId: Long
    ): GeneratedResponse {
        val lastReq = context.getLastRequest()
        val text = if (lastReq != null) {
            "ठीक है, पिछली request सफल नहीं हुई। मैं चाहें तो उसे दोबारा अलग तरीके से try कर सकती हूँ।"
        } else {
            "माफ़ कीजिए कि काम पूरा नहीं हुआ। मैंने इसे असफलता के रूप में नोट कर लिया है।"
        }
        return GeneratedResponse(
            text = text,
            responseType = ResponseType.CONVERSATION,
            requestId = requestId,
            pendingConfirmation = PendingConfirmation(
                type = PendingConfirmationType.RETRY_PREVIOUS,
                relatedRequestId = lastReq?.requestId,
                prompt = "Kya main dobara try karun?"
            )
        )
    }

    private fun generateRetryResponse(context: ConversationContext, requestId: Long): GeneratedResponse {
        val lastReq = context.getLastRequest()
        val text = if (lastReq != null) {
            "पिछला अनुरोध (\"${lastReq.rawUserCommand}\") फिर से दोहराया जा रहा है..."
        } else {
            "दोहराने के लिए कोई पिछला एक्शन नहीं मिला।"
        }
        return GeneratedResponse(
            text = text,
            responseType = ResponseType.ACTION_PROGRESS,
            requestId = requestId
        )
    }

    private fun generateCancelResponse(requestId: Long): GeneratedResponse {
        return GeneratedResponse(
            text = "कमांड रद्द कर दी गई है।",
            responseType = ResponseType.CANCELLATION,
            requestId = requestId,
            executionStatus = ExecutionStatus.CANCELLED
        )
    }

    private fun generateConfirmationResponse(context: ConversationContext, requestId: Long): GeneratedResponse {
        val pending = context.clearPendingConfirmation()
        return when (pending?.type) {
            PendingConfirmationType.RETRY_PREVIOUS -> {
                GeneratedResponse(
                    text = "ठीक है, मैं दोबारा कोशिश कर रही हूँ...",
                    responseType = ResponseType.ACTION_PROGRESS,
                    requestId = requestId
                )
            }
            PendingConfirmationType.NEED_MORE_HELP -> {
                GeneratedResponse(
                    text = "बताइए, मैं आपकी और क्या मदद करूँ?",
                    responseType = ResponseType.CONVERSATION,
                    requestId = requestId
                )
            }
            else -> {
                GeneratedResponse(
                    text = "जी ठीक है, बताइए क्या करना है।",
                    responseType = ResponseType.CONVERSATION,
                    requestId = requestId
                )
            }
        }
    }

    private fun generateDenialResponse(context: ConversationContext, requestId: Long): GeneratedResponse {
        val pending = context.clearPendingConfirmation()
        return when (pending?.type) {
            PendingConfirmationType.RETRY_PREVIOUS -> {
                GeneratedResponse(
                    text = "ठीक है, मैंने दोहराने की प्रक्रिया रद्द कर दी है।",
                    responseType = ResponseType.CONVERSATION,
                    requestId = requestId
                )
            }
            PendingConfirmationType.NEED_MORE_HELP -> {
                GeneratedResponse(
                    text = "ठीक है! जब भी जरूरत हो, मुझे बताइएगा।",
                    responseType = ResponseType.CONVERSATION,
                    requestId = requestId
                )
            }
            else -> {
                GeneratedResponse(
                    text = "जी ठीक है।",
                    responseType = ResponseType.CONVERSATION,
                    requestId = requestId
                )
            }
        }
    }

    private fun generateCorrectionResponse(
        intent: ParsedIntent,
        context: ConversationContext,
        requestId: Long
    ): GeneratedResponse {
        return GeneratedResponse(
            text = "माफ़ कीजिए, मेरी समझने में गलती हुई। मैंने इसे ठीक कर लिया है और अपनी समझ को अपडेट कर लिया है।",
            responseType = ResponseType.TEXT,
            requestId = requestId
        )
    }

    private fun generateMetaInstructionResponse(intent: ParsedIntent, requestId: Long): GeneratedResponse {
        return GeneratedResponse(
            text = "समझ गई। अब से मैं उपलब्ध सभी स्टेप्स अपने आप (automatically) पूरे करने की कोशिश करूँगी।",
            responseType = ResponseType.TEXT,
            requestId = requestId
        )
    }

    private fun generateMemoryQueryResponse(requestId: Long): GeneratedResponse {
        val text = "मुझे आपकी प्राथमिकताओं और सीखे गए स्किल्स की जानकारी याद है। कोई भी संवेदनशील डेटा केवल आपके डिवाइस पर स्थानीय और सुरक्षित रहता है।"
        return GeneratedResponse(
            text = text,
            responseType = ResponseType.TEXT,
            requestId = requestId
        )
    }

    private fun generatePreferenceResponse(intent: ParsedIntent, requestId: Long): GeneratedResponse {
        return GeneratedResponse(
            text = "आपकी प्राथमिकता सुरक्षित कर ली गई है।",
            responseType = ResponseType.TEXT,
            requestId = requestId
        )
    }

    fun generateActionResultResponse(
        result: DetailedExecutionResult,
        requestId: Long
    ): GeneratedResponse {
        // Multi-task handling (Section 25)
        if (result.subResults.isNotEmpty()) {
            return generateMultiTaskResponse(result, requestId)
        }

        val text = formatDetailedResultText(result)
        val responseType = when (result.finalStatus) {
            ExecutionStatus.SUCCESS -> ResponseType.ACTION_SUCCESS
            ExecutionStatus.PARTIAL_SUCCESS -> ResponseType.PARTIAL_SUCCESS
            ExecutionStatus.FAILED -> ResponseType.ACTION_FAILURE
            ExecutionStatus.CANCELLED -> ResponseType.CANCELLATION
            ExecutionStatus.IN_PROGRESS, ExecutionStatus.RUNNING -> ResponseType.ACTION_PROGRESS
            ExecutionStatus.NOT_EXECUTED, ExecutionStatus.NOT_STARTED, ExecutionStatus.PLANNED -> ResponseType.TEXT
        }

        return GeneratedResponse(
            text = text,
            responseType = responseType,
            requestId = requestId,
            executionStatus = result.finalStatus
        )
    }

    private fun generateMultiTaskResponse(
        result: DetailedExecutionResult,
        requestId: Long
    ): GeneratedResponse {
        val subResults = result.subResults
        val allSuccess = subResults.all { it.finalStatus == ExecutionStatus.SUCCESS && it.verificationStatus }
        val allFailed = subResults.all { it.finalStatus == ExecutionStatus.FAILED }

        if (allSuccess) {
            val text = "सभी कार्य सफलतापूर्वक पूरे और सत्यापित हुए।"
            return GeneratedResponse(
                text = text,
                responseType = ResponseType.ACTION_SUCCESS,
                requestId = requestId,
                executionStatus = ExecutionStatus.SUCCESS
            )
        }

        if (allFailed) {
            val text = "दिए गए कार्य पूरे नहीं हो सके।"
            return GeneratedResponse(
                text = text,
                responseType = ResponseType.ACTION_FAILURE,
                requestId = requestId,
                executionStatus = ExecutionStatus.FAILED
            )
        }

        // Mixed success / failure - Report independently!
        // E.g. "Desi Gamer वाला YouTube task पूरा नहीं हुआ, लेकिन Chrome में Craftland खोलने का task सफल रहा।"
        val descriptions = mutableListOf<String>()
        for (sub in subResults) {
            val app = sub.target?.replaceFirstChar { it.uppercase() } ?: sub.intent.app ?: "टास्क"
            val queryDesc = sub.intent.query?.let { "'$it' वाला " } ?: ""
            if (sub.finalStatus == ExecutionStatus.SUCCESS && sub.verificationStatus) {
                descriptions.add("$queryDesc$app खोलने का task सफल रहा")
            } else if (sub.finalStatus == ExecutionStatus.PARTIAL_SUCCESS) {
                descriptions.add("$queryDesc$app task आंशिक रूप से चला (playback/page सत्यापित नहीं हुआ)")
            } else {
                descriptions.add("$queryDesc$app task पूरा नहीं हुआ")
            }
        }

        val text = descriptions.joinToString("लेकिन ", postfix = "।") { "$it, " }
            .replace(", लेकिन ", ", लेकिन ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return GeneratedResponse(
            text = if (descriptions.size == 2) {
                "${descriptions[0]}, लेकिन ${descriptions[1]}।"
            } else {
                descriptions.joinToString("\n")
            },
            responseType = ResponseType.PARTIAL_SUCCESS,
            requestId = requestId,
            executionStatus = ExecutionStatus.PARTIAL_SUCCESS
        )
    }

    private fun formatDetailedResultText(result: DetailedExecutionResult): String {
        if (result.wasMisinterpreted) {
            return "मैंने उस request को सही तरह से execute नहीं किया। मैंने उसके text को गलती से YouTube search query समझ लिया था।"
        }

        if (result.intent.type == IntentType.META_INSTRUCTION || result.metaInstruction != null) {
            return "इस अनुरोध में आपने मुझे निर्देश दिया था कि मैं सारे स्टेप्स खुद करूँ। मैंने इस वरीयता (preference) को सेट कर लिया था।"
        }

        // Truthful reporting rule: If verification failed, NEVER claim success!
        if (result.finalStatus == ExecutionStatus.PARTIAL_SUCCESS ||
            (result.intent.type == IntentType.SEARCH_AND_PLAY && !result.verificationStatus) ||
            result.isSearchOnlyStarted
        ) {
            val q = result.intent.query ?: "वीडियो"
            return "मैंने YouTube पर '$q' की खोज शुरू की, लेकिन वीडियो play होना verify नहीं हो पाया (पुष्टि नहीं हुई)।"
        }

        if (result.finalStatus == ExecutionStatus.SUCCESS && result.verificationStatus) {
            return when (result.intent.type) {
                IntentType.SEARCH_AND_PLAY -> {
                    val q = result.intent.query ?: "वीडियो"
                    "YouTube पर '$q' का वीडियो चल रहा है (सत्यापित)।"
                }
                IntentType.OPEN_APP -> "${result.target ?: result.intent.app ?: "ऐप"} सफलतापूर्वक खोला गया।"
                IntentType.OPEN_PAGE -> "पेज '${result.intent.query ?: result.target}' सफलतापूर्वक लोड हुआ।"
                else -> result.summary ?: "काम पूरा हुआ और सत्यापित हुआ।"
            }
        }

        if (result.finalStatus == ExecutionStatus.FAILED) {
            val reason = result.failureReason ?: "स्क्रीन पर आवश्यक एलिमेंट नहीं मिला या एक्शन विफल रहा।"
            return "काम पूरा नहीं हो सका: $reason"
        }

        if (result.finalStatus == ExecutionStatus.CANCELLED) {
            return "अनुरोध रद्द कर दिया गया था।"
        }

        return result.summary ?: "अनुरोध की स्थिति: ${result.finalStatus}"
    }
}
