package com.example.brain

import android.util.Log
import com.example.models.DecisionSource
import com.example.models.ExecutionDecision
import com.example.models.IntentType
import com.example.models.MessageCategory
import com.example.models.ParsedCommand
import com.example.models.ParsedIntent

/**
 * RequestRouter routes the StructuredCommand (ParsedCommand) to appropriate execution engines:
 * - Conversation / Greeting -> ResponseEngine directly
 * - Context / Recall -> RecentRequestContext
 * - Status / Failure Explanation -> RecentRequestContext
 * - Meta Instruction -> Memory / Preference Store
 * - Search / Play / Action -> Action Execution Engine & Verifier
 * - Low Confidence -> Gemini Fallback
 *
 * Implements Safe Debug Logging according to Section 14.
 */
object RequestRouter {

    private const val TAG = "RequestRouter"

    data class RoutingPlan(
        val decision: ExecutionDecision,
        val mappedIntent: ParsedIntent,
        val isDirectResponse: Boolean = false,
        val needsGeminiFallback: Boolean = false
    )

    fun route(
        command: ParsedCommand,
        conversationContext: ConversationContext,
        recentRequestContext: RecentRequestContext
    ): RoutingPlan {
        // Safe Debug Logging (Section 14)
        logSafeDebug(command)

        // Check if confidence is low or unknown intent -> needs Gemini fallback
        if (command.confidence < 0.6f || command.intent == IntentType.UNKNOWN) {
            val mapped = mapToParsedIntent(command)
            return RoutingPlan(
                decision = ExecutionDecision(
                    source = DecisionSource.GEMINI_FALLBACK,
                    intent = mapped,
                    confidence = command.confidence,
                    explanation = "Confidence below threshold, routing to Gemini fallback"
                ),
                mappedIntent = mapped,
                isDirectResponse = false,
                needsGeminiFallback = true
            )
        }

        // Map ParsedCommand to ParsedIntent for execution
        val mappedIntent = mapToParsedIntent(command)

        // Check if intent requires direct conversational / status response
        val isDirect = when (command.intent) {
            IntentType.GREETING,
            IntentType.GENERAL_CONVERSATION,
            IntentType.QUESTION,
            IntentType.RECALL_REQUEST,
            IntentType.RECALL_RECENT_REQUESTS,
            IntentType.EXECUTION_STATUS_QUERY,
            IntentType.EXPLAIN_LAST_FAILURE,
            IntentType.FAILURE_FEEDBACK,
            IntentType.REPORT_FAILURE,
            IntentType.CORRECTION,
            IntentType.CORRECT_PREVIOUS_RESULT,
            IntentType.EXECUTION_PREFERENCE,
            IntentType.META_INSTRUCTION,
            IntentType.MEMORY_QUERY,
            IntentType.CONFIRMATION,
            IntentType.DENIAL -> true
            else -> false
        }

        val decision = ExecutionDecision(
            source = DecisionSource.LOCAL_PARSER,
            intent = mappedIntent,
            confidence = command.confidence,
            explanation = "Locally parsed and encoded as ${command.intent} (category=${command.category})"
        )

        return RoutingPlan(
            decision = decision,
            mappedIntent = mappedIntent,
            isDirectResponse = isDirect,
            needsGeminiFallback = false
        )
    }

    fun mapToParsedIntent(cmd: ParsedCommand): ParsedIntent {
        val subIntents = cmd.subCommands.map { mapToParsedIntent(it) }
        return ParsedIntent(
            type = cmd.intent,
            app = cmd.targetApp,
            target = cmd.target ?: cmd.targetApp,
            query = cmd.query,
            action = cmd.action,
            confidence = cmd.confidence,
            category = cmd.category,
            targetIndex = cmd.contextReference?.index,
            referenceType = cmd.contextReference?.type?.name,
            userCorrection = cmd.parameters["correctionText"],
            executionPreference = cmd.preference,
            metaInstruction = cmd.preference,
            actions = subIntents,
            subIntents = subIntents
        )
    }

    private fun logSafeDebug(cmd: ParsedCommand) {
        // Redact any possible sensitive information like passwords, tokens, keys
        val safeRaw = PrivacyFilter.redactSensitive(cmd.rawText)
        val safeNorm = PrivacyFilter.redactSensitive(cmd.normalizedText)
        val safeQuery = cmd.query?.let { PrivacyFilter.redactSensitive(it) }

        val logLines = buildString {
            append("\n================ MYRA COMMAND ENCODING ================\n")
            append("RAW:        $safeRaw\n")
            append("NORMALIZED: $safeNorm\n")
            append("INTENT:     ${cmd.intent}\n")
            append("APP:        ${cmd.targetApp ?: "None"}\n")
            append("QUERY:      ${safeQuery ?: "None"}\n")
            append("ACTION:     ${cmd.action ?: "None"}\n")
            append("CONFIDENCE: ${if (cmd.confidence >= 0.9f) "HIGH" else if (cmd.confidence >= 0.6f) "MEDIUM" else "LOW"}\n")
            if (cmd.contextReference != null) {
                append("CONTEXT_REF: ${cmd.contextReference.type} index=${cmd.contextReference.index}\n")
            }
            if (cmd.preference != null) {
                append("PREFERENCE: ${cmd.preference}\n")
            }
            if (cmd.subCommands.isNotEmpty()) {
                append("SUB_COMMANDS: ${cmd.subCommands.size} sub-tasks\n")
            }
            append("=======================================================")
        }
        Log.i(TAG, logLines)
    }
}
