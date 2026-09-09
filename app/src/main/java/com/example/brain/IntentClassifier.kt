package com.example.brain

import com.example.models.ContextReference
import com.example.models.IntentType
import com.example.models.MessageCategory

/**
 * IntentClassifier explicitly categorizes user queries into distinct IntentTypes and MessageCategories.
 *
 * CRITICAL SAFETY RULES:
 * - Meta instructions ("me chahta hu vo tum khud karo") must NEVER become SEARCH.
 * - Failure feedback ("nahi hua", "video play nahi hua tha") must NEVER become SEARCH.
 * - Context recall ("mene kya bola") and execution status queries ("1 wale me tumne kya kiya") must NEVER become SEARCH.
 * - Intent must be decided before search query extraction.
 */
object IntentClassifier {

    data class ClassificationResult(
        val intent: IntentType,
        val category: MessageCategory,
        val confidence: Float = 1.0f,
        val contextReference: ContextReference? = null,
        val isMultiCommand: Boolean = false,
        val preference: String? = null
    )

    fun classify(normalizedText: String, rawText: String): ClassificationResult {
        val normalized = normalizedText.lowercase().trim()
        val contextRef = ContextResolver.extractReference(normalized)

        // 1. Meta Instruction / Execution Preference
        // Example: "me chahta hu vo sare steps youtube par ho or vo tum khud kro"
        if (isMetaInstruction(normalized)) {
            return ClassificationResult(
                intent = IntentType.EXECUTION_PREFERENCE,
                category = MessageCategory.META_INSTRUCTION,
                confidence = 1.0f,
                preference = "assistant_should_perform_steps_itself"
            )
        }

        // 2. API Key Status Query (MUST NEVER BECOME SEARCH OR GENERIC)
        // Example: "api key lgi ya nhi", "api key lagi hai?", "gemini key configured hai?"
        if (isApiKeyStatusQuery(normalized)) {
            return ClassificationResult(
                intent = IntentType.API_KEY_STATUS_QUERY,
                category = MessageCategory.API_KEY_STATUS_QUERY,
                confidence = 1.0f
            )
        }

        // 3. Meta Conversation / Dialogue Alignment
        // Example: "me kya bol rha hu or tum kya bol rhe ho"
        if (isMetaConversation(normalized)) {
            return ClassificationResult(
                intent = IntentType.META_CONVERSATION,
                category = MessageCategory.META_CONVERSATION,
                confidence = 1.0f
            )
        }

        // 4. Why Query / Explanation of previous statement or action
        // Example: "kyo", "kyu", "why?", "aisa kyu?"
        if (isWhyQuery(normalized)) {
            return ClassificationResult(
                intent = IntentType.WHY_QUERY,
                category = MessageCategory.WHY_QUESTION,
                confidence = 1.0f,
                contextReference = contextRef
            )
        }

        // 5. Correction
        // Example: "nahi mera matlab...", "maine search karne ko nahi kaha tha", "maine ye nahi bola", "ye nahi bola tha"
        if (isCorrection(normalized)) {
            return ClassificationResult(
                intent = IntentType.CORRECTION,
                category = MessageCategory.CORRECTION,
                confidence = 1.0f,
                contextReference = contextRef
            )
        }

        // 6. Execution Status Queries & Failure Explanations
        // Example: "1 wale me tumne kya kiya", "tumne kya kiya", "kyu nahi hua"
        if (isExecutionStatusQuery(normalized)) {
            val intent = if (isWhyFailureQuery(normalized)) {
                IntentType.EXPLAIN_LAST_FAILURE
            } else {
                IntentType.EXECUTION_STATUS_QUERY
            }
            return ClassificationResult(
                intent = intent,
                category = MessageCategory.EXECUTION_STATUS,
                confidence = 1.0f,
                contextReference = contextRef
            )
        }

        // 7. Failure Feedback
        // Example: "nahi hua", "nahi hua or youtube par video play bhi nhi hua tha", "kuch nahi hua"
        // MUST NEVER BE TREATED AS SEARCH!
        if (isFailureFeedback(normalized)) {
            return ClassificationResult(
                intent = IntentType.FAILURE_FEEDBACK,
                category = MessageCategory.FAILURE_FEEDBACK,
                confidence = 1.0f,
                contextReference = contextRef
            )
        }

        // 8. Assistant Recall Query ("tumne kya bola", "tumne kya kaha", "what did you say")
        if (isAssistantRecallQuery(normalized)) {
            return ClassificationResult(
                intent = IntentType.ASSISTANT_RECALL_QUERY,
                category = MessageCategory.ASSISTANT_RECALL_QUERY,
                confidence = 1.0f,
                contextReference = contextRef,
                preference = "assistant_response"
            )
        }

        // 8b. Context Query ("mene kya pucha" -> previous user question/message)
        if (isContextQuery(normalized)) {
            return ClassificationResult(
                intent = IntentType.CONTEXT_QUERY,
                category = MessageCategory.CONTEXT_QUESTION,
                confidence = 1.0f,
                contextReference = contextRef,
                preference = "user_question"
            )
        }

        // 9. Recall Requests ("mene kya bola tha" -> previous user request/command)
        if (isRecallQuery(normalized)) {
            return ClassificationResult(
                intent = IntentType.RECALL_REQUEST,
                category = MessageCategory.CONTEXT_QUESTION,
                confidence = 1.0f,
                contextReference = contextRef
            )
        }

        // 10. Retry Request
        // Example: "dobara karo", "phir se karo", "repeat karo", "usi ko dobara karo"
        if (isRetryRequest(normalized)) {
            return ClassificationResult(
                intent = IntentType.RETRY_REQUEST,
                category = MessageCategory.RETRY_REQUEST,
                confidence = 1.0f,
                contextReference = contextRef
            )
        }

        // 11. Cancel Request
        // Example: "cancel karo", "ruk jao", "rok do", "stop"
        if (isCancelRequest(normalized)) {
            return ClassificationResult(
                intent = IntentType.CANCEL_REQUEST,
                category = MessageCategory.CANCEL_REQUEST,
                confidence = 1.0f
            )
        }

        // 8. Confirmation & Denial
        if (isConfirmation(normalized)) {
            return ClassificationResult(
                intent = IntentType.CONFIRMATION,
                category = MessageCategory.CONFIRMATION,
                confidence = 1.0f
            )
        }
        if (isDenial(normalized)) {
            return ClassificationResult(
                intent = IntentType.DENIAL,
                category = MessageCategory.DENIAL,
                confidence = 1.0f
            )
        }

        // 9. Memory Query
        // Example: "tumhe kya yaad hai", "meri preferences kya hain"
        if (isMemoryQuery(normalized)) {
            return ClassificationResult(
                intent = IntentType.MEMORY_QUERY,
                category = MessageCategory.MEMORY_QUERY,
                confidence = 1.0f
            )
        }

        // 10. Greetings & General Conversation
        if (isGreeting(normalized)) {
            return ClassificationResult(
                intent = IntentType.GREETING,
                category = MessageCategory.GREETING,
                confidence = 1.0f
            )
        }
        if (isGeneralConversation(normalized)) {
            return ClassificationResult(
                intent = IntentType.GENERAL_CONVERSATION,
                category = MessageCategory.GENERAL_CONVERSATION,
                confidence = 1.0f
            )
        }
        if (isIdentityQuestion(normalized)) {
            return ClassificationResult(
                intent = IntentType.IDENTITY_QUESTION,
                category = MessageCategory.IDENTITY_QUESTION,
                confidence = 1.0f
            )
        }

        // 11. Challenge / Autonomous multi-step request
        if (isChallengeRequest(normalized)) {
            return ClassificationResult(
                intent = IntentType.CHALLENGE_REQUEST,
                category = MessageCategory.NEW_COMMAND,
                confidence = 1.0f
            )
        }

        // 12. Multi-intent / Action Chain
        // Example: "chrome kholo aur google par free fire search karo"
        if (isMultiCommand(normalized)) {
            return ClassificationResult(
                intent = IntentType.ACTION_CHAIN,
                category = MessageCategory.NEW_COMMAND,
                confidence = 0.95f,
                isMultiCommand = true
            )
        }

        // 13. Search and Play / Search / Open App / Navigate
        val actionIntent = classifyActionIntent(normalized)
        return ClassificationResult(
            intent = actionIntent,
            category = MessageCategory.NEW_COMMAND,
            confidence = 1.0f
        )
    }

    private fun isMetaInstruction(normalized: String): Boolean {
        val metaPatterns = listOf(
            "tum khud karo", "tum khud kro", "sare steps tum karo", "sare steps tum kro",
            "khud karo", "khud kro", "apne aap karo", "apne aap kro", "automatically karo",
            "chahta hu vo sare steps", "chahta hu ki sare steps", "me chahta hu", "main chahta hu",
            "mai chahta hu", "steps youtube par ho or vo tum khud", "steps youtube par ho aur vo tum khud",
            "vo tum khud", "wo tum khud", "tumhare dwara ho", "mere bina bole karo",
            "automatic steps", "steps automatically"
        )
        return metaPatterns.any { normalized.contains(it) }
    }

    private fun isCorrection(normalized: String): Boolean {
        val correctionPhrases = listOf(
            "nahi mera matlab", "mera matlab ye tha", "mera matlab kuch aur tha",
            "mera matlab ye nahi tha", "mera matlab yeh nahi tha", "maine search karne ko nahi kaha tha",
            "maine search karne ko nahi bola tha", "maine search nahi bola tha",
            "maine ye nahi bola", "maine ye nahi kaha", "maine yeh nahi bola", "maine yeh nahi kaha",
            "ye nahi bola tha", "ye nahi kaha tha", "yeh nahi bola tha", "yeh nahi kaha tha",
            "galat hai", "ye galat hai", "yeh galat hai", "wrong hai", "aisa nahi", "aisa nahi bola tha",
            "tum galat samjhe", "tumne galat samjha", "गलत है", "ऐसा नहीं"
        )
        return correctionPhrases.any { normalized.contains(it) }
    }

    private fun isExecutionStatusQuery(normalized: String): Boolean {
        if (isWhyFailureQuery(normalized)) return true

        val statusPhrases = listOf(
            "tumne kya kiya", "kya kiya tumne", "hua kya", "kaam hua", "status kya hai",
            "what did you do", "kya hua", "kya chal raha hai", "tumne kya kara", "tumne kya kiya tha",
            "क्या किया तुमने", "तुमने क्या किया", "हुआ क्या", "काम हुआ"
        )
        val hasStatusWord = statusPhrases.any { normalized.contains(it) }
        val hasIndexRef = normalized.contains("wale me") || normalized.contains("waale me") ||
                normalized.contains("me tumne kya") || normalized.contains("mein tumne kya")
        return hasStatusWord || hasIndexRef
    }

    private fun isWhyFailureQuery(normalized: String): Boolean {
        val whyPhrases = listOf(
            "kyu nahi hua", "kyun nahi hua", "kyu fail hua", "kyun fail hua", "fail kyu hua",
            "fail kyun hua", "why did it fail", "why didn't it work", "क्यों नहीं हुआ",
            "फेल क्यों हुआ", "kyu ruk gaya", "kyun ruk gaya", "kaam kyu nahi hua"
        )
        return whyPhrases.any { normalized.contains(it) }
    }

    private fun isFailureFeedback(normalized: String): Boolean {
        if (isWhyFailureQuery(normalized)) return false

        val failurePhrases = listOf(
            "nahi hua", "kuch nahi hua", "ye nahi hua", "fail hua", "fail ho gaya",
            "kaam nahi hua", "nahi chala", "video nahi chala", "chala nahi",
            "kuch bhi nahi hua", "not working", "didn't work", "failed",
            "video play nahi hua", "video play bhi nahi hua", "video play bhi nhi hua",
            "play nahi hua", "play bhi nahi hua", "नहीं हुआ", "कुछ नहीं हुआ", "फेल हो गया",
            "काम नहीं हुआ", "नहीं चला", "play nahi hua tha", "video play nahi hua tha",
            "video play bhi nhi hua tha", "video play bhi nahi hua tha"
        )

        return failurePhrases.any { normalized.contains(it) }
    }

    private fun isApiKeyStatusQuery(normalized: String): Boolean {
        val hasKeyWord = normalized.contains("api key") ||
                normalized.contains("apikey") ||
                normalized.contains("api ki") ||
                normalized.contains("gemini key") ||
                normalized.contains("gemini api") ||
                (normalized.contains("key") && (normalized.contains("gemini") || normalized.contains("api") || normalized.contains("status")))
        val statusWords = listOf(
            "lgi ya nhi", "lagi ya nahi", "lagi hai", "lgi hai", "lagi h", "lgi h",
            "status", "configured", "set hai", "hai ya nahi", "hai ya nhi", "dali hai",
            "daali hai", "save hai", "check", "batao", "hai", "लगी है", "लगी या नहीं", "स्टेटस", "सेट है"
        )
        val directKeyPhrases = listOf(
            "api key lgi ya nhi", "api key lagi ya nahi", "api key lagi hai", "api key lgi hai",
            "api key status", "api key status kya hai", "api key ka status", "api key ka status kya hai",
            "gemini key configured hai", "key set hai ya nahi", "key set hai ya nhi", "gemini key lagi hai",
            "key lagi hai ya nahi", "api key lagi hai ya nahi", "api key lagi hai?", "api key lgi hai?",
            "api key lgi ya nahi", "api key lagi ya nhi"
        )
        return directKeyPhrases.any { normalized.contains(it) } || (hasKeyWord && statusWords.any { normalized.contains(it) })
    }

    private fun isMetaConversation(normalized: String): Boolean {
        val metaConvPhrases = listOf(
            "me kya bol rha hu or tum kya bol rhe ho",
            "me kya bol raha hu or tum kya bol rahe ho",
            "me kya bol raha hu aur tum kya bol rahe ho",
            "main kya bol raha hu aur tum kya bol rahe ho",
            "mai kya bol raha hu aur tum kya bol rahe ho",
            "me kya bol rha hu aur tum kya bol rhe ho",
            "hum kya baat kar rahe", "hum kya baat kar rahe the",
            "tum kya bol rahe ho", "kya bol rahe ho tum", "tum kya bol rahi ho",
            "kya bol rhe ho", "tum kya bol rhe ho",
            "me kya bol raha hu", "me kya bol rha hu", "mai kya bol raha hu",
            "me kya pooch raha hu", "me kya puch raha hu"
        )
        return metaConvPhrases.any { normalized.contains(it) }
    }

    private fun isWhyQuery(normalized: String): Boolean {
        val cleaned = normalized.trim().removeSuffix("?").removeSuffix("!").removeSuffix("।").trim()
        val whyExact = setOf(
            "kyo", "kyu", "kyun", "why", "aisa kyu", "aisa kyo", "aisa kyun",
            "fir kyu", "fir kyo", "phir kyu", "phir kyo", "ye kyu", "ye kyo", "yeh kyu",
            "yeh kyo", "aisa kyu hua", "kyu aisa", "क्यों", "ऐसा क्यों", "फिर क्यों",
            "यह क्यों", "ये क्यों"
        )
        return whyExact.contains(cleaned) ||
                normalized.matches(Regex("(?i)^(kyo|kyu|kyun|why|aisa kyu|fir kyu|ye kyu|phir kyu|yeh kyu)[?!.]*$"))
    }

    private fun isAssistantRecallQuery(normalized: String): Boolean {
        val phrases = listOf(
            "tumne kya bola", "tumne kya kaha", "tumne kya bola tha", "tumne kya kaha tha",
            "tumne abhi kya bola", "tumne abhi kya kaha", "what did you say", "what did you tell me",
            "तुमने क्या बोला", "तुमने क्या कहा", "आपने क्या कहा", "आपने क्या बोला"
        )
        return phrases.any { normalized.contains(it) }
    }

    private fun isContextQuery(normalized: String): Boolean {
        if (isAssistantRecallQuery(normalized)) return false
        val contextPhrases = listOf(
            "mene kya pucha", "maine kya pucha", "mene kya poocha", "maine kya poocha",
            "mene kya pucha tha", "maine kya pucha tha", "mene kya poocha tha", "maine kya poocha tha",
            "maine abhi kya pucha", "mene abhi kya pucha",
            "what did i ask", "what did i ask you", "मैंने क्या पूछा", "मैंने क्या पूछा था"
        )
        return contextPhrases.any { normalized.contains(it) }
    }

    private fun isRecallQuery(normalized: String): Boolean {
        if (isContextQuery(normalized) || isAssistantRecallQuery(normalized)) return false
        val recallPhrases = listOf(
            "mene kya bola tha", "maine kya bola tha", "mene kya bola", "maine kya bola",
            "maine kya kaha tha", "mene kya kaha tha", "maine kya kaha", "mene kya kaha",
            "maine abhi kya bola", "mene abhi kya bola", "maine abhi kya kaha", "mene abhi kya kaha",
            "what did i say", "what did i just say", "what was my last message",
            "मैंने क्या बोला था", "मैंने क्या बोला", "मैंने क्या कहा था", "मैंने क्या कहा"
        )
        return recallPhrases.any { normalized.contains(it) || it.contains(normalized) } ||
                normalized.matches(Regex("(?i)^(mene|maine|hamne|मैंने)\\s+(kya|tumhe\\s+kya).*"))
    }

    private fun isRetryRequest(normalized: String): Boolean {
        val retryPhrases = listOf(
            "dobara karo", "phir se karo", "fir se karo", "repeat karo", "repeat", "try again",
            "dobaara karo", "phirse karo", "firse karo", "usi ko dobara karo", "usi ko phir se karo",
            "दोबारा करो", "फिर से करो"
        )
        return retryPhrases.any { normalized.contains(it) }
    }

    private fun isCancelRequest(normalized: String): Boolean {
        val cancelPhrases = listOf(
            "cancel", "cancel karo", "rok do", "rehne do", "radd karo", "रद्द करो",
            "रहने दो", "रोक दो", "stop action", "stop", "band karo", "ruk jao", "रुक जाओ"
        )
        return cancelPhrases.any { normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") }
    }

    private fun isConfirmation(normalized: String): Boolean {
        val confirmWords = listOf("haan", "ha", "haa", "yes", "sahi hai", "theek hai karo", "kardo", "kar do", "हाँ", "हा", "हाँ करो", "sure")
        return confirmWords.any { normalized == it || normalized == "$it myra" || normalized == "ha karo" || normalized == "haan karo" }
    }

    private fun isDenial(normalized: String): Boolean {
        val denyWords = listOf("nahi", "no", "mat karo", "rehne do", "don't", "nahi rehne do", "नहीं", "ना", "bilkul nahi")
        return denyWords.any { normalized == it || normalized == "nahi myra" || normalized == "no thanks" }
    }

    private fun isMemoryQuery(normalized: String): Boolean {
        val memoryPhrases = listOf(
            "tumhe kya yaad hai", "meri preferences kya hain", "tumne kya yaad rakha hai",
            "what do you remember", "kya yaad hai tumhe", "meri memory", "yaad kya hai"
        )
        return memoryPhrases.any { normalized.contains(it) }
    }

    private fun isGreeting(normalized: String): Boolean {
        val greetings = listOf("hii", "hi", "hello", "hey", "namaste", "नमस्ते", "हेलो", "good morning", "good evening")
        return greetings.any { normalized == it || normalized.startsWith("$it ") }
    }

    private fun isGeneralConversation(normalized: String): Boolean {
        val convPhrases = listOf("kaise ho", "kya haal hai", "sab theek", "theek hai", "accha", "how are you", "all good", "kaisa chal raha hai")
        return convPhrases.any { normalized == it || normalized.startsWith("$it ") }
    }

    private fun isIdentityQuestion(normalized: String): Boolean {
        val idPhrases = listOf(
            "tum kon ho", "tum kaun ho", "tum koun ho", "kon ho tum", "kaun ho tum", "koun ho tum",
            "aap kon ho", "aap kaun ho", "aap koun ho", "who are you", "who are u", "who r u",
            "tum kya ho", "aap kya ho", "myra kon hai", "myra kaun hai", "myra kya hai",
            "myra kaun ho", "myra kon ho", "tumhara naam kya hai", "tera naam kya hai",
            "what is your name", "what are you", "तुम कौन हो", "आप कौन हैं", "कौन हो तुम"
        )
        return idPhrases.any { normalized == it || normalized.contains(it) }
    }

    private fun isChallengeRequest(normalized: String): Boolean {
        return normalized.contains("random target") && (normalized.contains("6 step") || normalized.contains("6 steps") || normalized.contains("६ स्टेप"))
    }

    private fun isMultiCommand(normalized: String): Boolean {
        val splitRegex = Regex("(?i)\\s+(aur|और|and|phir|फिर)\\s+")
        return splitRegex.containsMatchIn(normalized)
    }

    private fun classifyActionIntent(normalized: String): IntentType {
        // Video play patterns -> SEARCH_AND_PLAY
        val isVideoPlay = (normalized.contains("video") || normalized.contains("song") || normalized.contains("gaana") ||
                normalized.contains("play") || normalized.contains("chalao") || normalized.contains("chala do") ||
                normalized.contains("प्ले") || normalized.contains("चलाओ") || normalized.contains("चला दो")) &&
                (normalized.contains("youtube") || normalized.contains("yt") || normalized.contains("गाना"))
        if (isVideoPlay) {
            return IntentType.SEARCH_AND_PLAY
        }

        // Search patterns -> SEARCH
        val isSearch = normalized.contains("search") || normalized.contains("khojo") ||
                normalized.contains("dhoondo") || normalized.contains("खोजो") || normalized.contains("ढूंढो")
        if (isSearch) {
            return IntentType.SEARCH
        }

        // Open app patterns -> OPEN_APP
        val isOpenApp = normalized.contains("kholo") || normalized.contains("open") ||
                normalized.contains("खोलो") || normalized.contains("start")
        if (isOpenApp) {
            return IntentType.OPEN_APP
        }

        // Navigation
        val isNav = normalized.contains("settings") || normalized.contains("home") || normalized.contains("back")
        if (isNav) {
            return IntentType.NAVIGATE
        }

        return IntentType.NEW_COMMAND
    }
}
