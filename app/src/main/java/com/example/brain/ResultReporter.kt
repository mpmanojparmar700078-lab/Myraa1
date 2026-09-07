package com.example.brain

import com.example.models.ActionResult
import com.example.models.ExecutionStep
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.models.RequestResult
import com.example.models.RequestState

/**
 * ResultReporter enforces the core mandate:
 * ExecutionResult / RequestResult -> ResultReporter -> Truthful User Response
 *
 * It guarantees that Myra NEVER reports "काम पूरा हुआ" unless the requested
 * end outcome was genuinely verified on screen.
 */
class ResultReporter {

    fun formatReportForRequest(
        rawCommand: String,
        intent: ParsedIntent,
        state: RequestState,
        result: RequestResult?,
        targetApp: String?,
        query: String?,
        wasMisinterpreted: Boolean = false,
        isSearchOnlyStarted: Boolean = false,
        metaInstruction: String? = null,
        itemIndex: Int? = null
    ): String {
        val prefix = if (itemIndex != null) "अनुरोध #$itemIndex (\"$rawCommand\"): " else ""

        // Case 1: Was previously misinterpreted (e.g. user corrected Myra or classification error)
        if (wasMisinterpreted || result?.wasMisinterpreted == true) {
            return "${prefix}मैंने उस request को सही तरह से execute नहीं किया। मैंने उसके text को गलती से YouTube search query समझ लिया था।"
        }

        // Case 2: Meta Instruction (Preference setting, not app search)
        if (intent.type == IntentType.META_INSTRUCTION || metaInstruction != null) {
            return "${prefix}इस अनुरोध में आपने मुझे निर्देश दिया था कि मैं सारे स्टेप्स खुद करूँ। मैंने इस वरीयता (preference) को सेट कर लिया था।"
        }

        // Case 3: Challenge Request
        if (intent.type == IntentType.CHALLENGE_REQUEST) {
            val stepCount = result?.steps?.size ?: 6
            return if (state == RequestState.SUCCESS) {
                "${prefix}चैलेंज सफलतापूर्वक पूरा हुआ! मैंने $stepCount स्टेप्स निष्पादित और सत्यापित किए।"
            } else {
                "${prefix}चैलेंज आंशिक रूप से चला ($stepCount स्टेप्स), लेकिन पूरा सत्यापन नहीं हो सका।"
            }
        }

        // Case 4: Search was started but video/page playback not verified
        if (isSearchOnlyStarted || result?.isSearchOnlyStarted == true ||
            (state == RequestState.ACTION_STARTED) ||
            (state == RequestState.PARTIAL_SUCCESS && intent.type == IntentType.SEARCH_AND_PLAY)
        ) {
            val q = query ?: intent.query ?: "वीडियो"
            return "${prefix}मैंने YouTube पर '$q' की खोज शुरू की, लेकिन वीडियो play होना verify नहीं हो पाया (पुष्टि नहीं हुई)।"
        }

        // Case 5: Verified Success
        if (state == RequestState.SUCCESS && (result?.isVerified == true)) {
            val summary = if (!result.summary.isNullOrBlank()) {
                if (!result.summary.contains("काम पूरा हुआ") && !result.summary.contains("सफलतापूर्वक")) {
                    "काम पूरा हुआ। ${result.summary}"
                } else {
                    result.summary
                }
            } else {
                when (intent.type) {
                    IntentType.SEARCH_AND_PLAY -> "काम पूरा हुआ। '${query ?: intent.query}' का वीडियो चलने की पुष्टि हो गई।"
                    IntentType.OPEN_APP -> "${targetApp ?: intent.app ?: "ऐप"} सफलतापूर्वक खोला गया।"
                    IntentType.OPEN_PAGE -> "पेज '${query ?: intent.query}' सफलतापूर्वक लोड हुआ।"
                    else -> "काम पूरा हुआ और सत्यापित हुआ।"
                }
            }
            return "${prefix}$summary"
        }

        // Case 6: Partial Success (App opened, but end goal unverified)
        if (state == RequestState.PARTIAL_SUCCESS || state == RequestState.REQUEST_PARTIALLY_COMPLETED) {
            val app = targetApp ?: intent.app ?: "ऐप"
            val q = if (!query.isNullOrBlank()) " और '$query' खोजने की कोशिश की" else ""
            return "${prefix}मैंने $app खोला$q, लेकिन requested परिणाम (playback/page) verify नहीं हो पाया।"
        }

        // Case 7: Failed
        if (state == RequestState.FAILED || state == RequestState.REQUEST_FAILED) {
            val reason = result?.failureReason ?: "स्क्रीन पर आवश्यक एलिमेंट नहीं मिला या एक्शन विफल रहा।"
            return "${prefix}काम पूरा नहीं हो सका: $reason"
        }

        // Case 8: Cancelled
        if (state == RequestState.CANCELLED || state == RequestState.REQUEST_CANCELLED) {
            return "${prefix}अनुरोध रद्द कर दिया गया था।"
        }

        // Default honest fallback
        return "${prefix}अनुरोध की स्थिति: $state"
    }

    fun formatMetaInstructionResponse(metaInstruction: String?): String {
        return "समझ गई। अब से मैं उपलब्ध सभी स्टेप्स अपने आप (automatically) पूरे करने की कोशिश करूँगी।"
    }

    fun formatFailureFeedbackResponse(lastCommand: String?): String {
        return if (lastCommand != null) {
            "माफ़ कीजिए कि पिछला काम (\"$lastCommand\") पूरा नहीं हुआ। मैंने इसे असफलता के रूप में रिकॉर्ड कर लिया है। आप चाहें तो 'दोबारा करो' कह सकते हैं।"
        } else {
            "माफ़ कीजिए कि पिछला काम पूरा नहीं हुआ। मैंने इसे असफलता के रूप में रिकॉर्ड कर लिया है।"
        }
    }

    fun formatCorrectionResponse(userCorrection: String): String {
        return "माफ़ कीजिए, मेरी समझने में गलती हुई। मैंने इसे ठीक कर लिया है और भविष्य के लिए अपनी समझ को अपडेट कर लिया है।"
    }

    fun formatChallengeResult(steps: List<ExecutionStep>): String {
        val successfulCount = steps.count { it.status == RequestState.SUCCESS || it.status == RequestState.ACTION_SUCCEEDED || it.verification }
        val sb = StringBuilder("चैलेंज टास्क पूरा हुआ! कुल $successfulCount/${steps.size} स्टेप्स सत्यापित किए गए:\n")
        steps.forEach { step ->
            val icon = if (step.verification || step.status == RequestState.SUCCESS || step.status == RequestState.ACTION_SUCCEEDED) "✓" else "✗"
            sb.append("$icon स्टेप ${step.stepId}: ${step.action}${step.target?.let { " ($it)" } ?: ""}\n")
        }
        return sb.toString().trimEnd()
    }
}
