package com.example.brain

import com.example.models.ContextReference
import com.example.models.ContextReferenceType
import com.example.models.IntentType
import com.example.models.MessageCategory
import com.example.models.ParsedCommand

/**
 * CommandEncoder converts raw text, normalized text, and classification results
 * into a structured ParsedCommand with semantic boundaries.
 *
 * CRITICAL REQUIREMENTS:
 * - Never extract search query from feedback, context, recall, meta, or retry messages.
 * - Semantic extraction for "youtube par X ka video play kro" -> query = "X", action = "PLAY_VIDEO".
 * - Multi-intent splitting into structured sub-commands.
 */
object CommandEncoder {

    fun encode(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        // Multi-command handling
        if (classification.isMultiCommand) {
            val subCommands = splitAndEncodeSubCommands(rawText, normalizedText)
            if (subCommands.size >= 2) {
                return ParsedCommand(
                    rawText = rawText,
                    normalizedText = normalizedText,
                    intent = IntentType.ACTION_CHAIN,
                    category = MessageCategory.NEW_COMMAND,
                    subCommands = subCommands,
                    confidence = 0.95f
                )
            }
        }

        // Single-command encoding based on classified IntentType
        return when (classification.intent) {
            IntentType.API_KEY_STATUS_QUERY -> encodeApiKeyStatusQuery(rawText, normalizedText, classification)
            IntentType.CONTEXT_QUERY -> encodeContextQuery(rawText, normalizedText, classification)
            IntentType.WHY_QUERY -> encodeWhyQuery(rawText, normalizedText, classification)
            IntentType.META_CONVERSATION -> encodeMetaConversation(rawText, normalizedText, classification)
            IntentType.SEARCH_AND_PLAY -> encodeSearchAndPlay(rawText, normalizedText, classification)
            IntentType.SEARCH -> encodeSearch(rawText, normalizedText, classification)
            IntentType.OPEN_APP -> encodeOpenApp(rawText, normalizedText, classification)
            IntentType.NAVIGATE -> encodeNavigate(rawText, normalizedText, classification)
            IntentType.EXECUTION_PREFERENCE, IntentType.META_INSTRUCTION -> encodeMetaInstruction(rawText, normalizedText, classification)
            IntentType.FAILURE_FEEDBACK, IntentType.REPORT_FAILURE -> encodeFailureFeedback(rawText, normalizedText, classification)
            IntentType.CORRECTION, IntentType.CORRECT_PREVIOUS_RESULT -> encodeCorrection(rawText, normalizedText, classification)
            IntentType.EXECUTION_STATUS_QUERY, IntentType.EXPLAIN_LAST_FAILURE -> encodeStatusQuery(rawText, normalizedText, classification)
            IntentType.RECALL_REQUEST, IntentType.RECALL_RECENT_REQUESTS -> encodeRecallQuery(rawText, normalizedText, classification)
            IntentType.RETRY_REQUEST, IntentType.RETRY_LAST_REQUEST -> encodeRetryRequest(rawText, normalizedText, classification)
            IntentType.CANCEL_REQUEST -> encodeCancelRequest(rawText, normalizedText, classification)
            IntentType.CONFIRMATION -> encodeConfirmation(rawText, normalizedText, classification)
            IntentType.DENIAL -> encodeDenial(rawText, normalizedText, classification)
            IntentType.MEMORY_QUERY -> encodeMemoryQuery(rawText, normalizedText, classification)
            IntentType.GREETING -> encodeGreeting(rawText, normalizedText, classification)
            IntentType.GENERAL_CONVERSATION -> encodeConversation(rawText, normalizedText, classification)
            IntentType.QUESTION -> encodeQuestion(rawText, normalizedText, classification)
            IntentType.CHALLENGE_REQUEST -> encodeChallenge(rawText, normalizedText, classification)
            else -> encodeGenericCommand(rawText, normalizedText, classification)
        }
    }

    private fun encodeSearchAndPlay(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        val targetApp = detectTargetApp(normalizedText) ?: "YouTube"
        val query = extractSearchAndPlayQuery(normalizedText)

        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.SEARCH_AND_PLAY,
            targetApp = targetApp,
            query = query,
            action = "PLAY_VIDEO",
            category = MessageCategory.NEW_COMMAND,
            confidence = if (query.isNotBlank()) 1.0f else 0.8f
        )
    }

    private fun encodeSearch(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        val targetApp = detectTargetApp(normalizedText) ?: "Google"
        val query = extractGeneralSearchQuery(normalizedText)

        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.SEARCH,
            targetApp = targetApp,
            query = query,
            action = "SEARCH",
            category = MessageCategory.NEW_COMMAND,
            confidence = if (query.isNotBlank()) 1.0f else 0.8f
        )
    }

    private fun encodeOpenApp(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        val targetApp = detectTargetApp(normalizedText) ?: extractAppNameFromOpenCommand(normalizedText)

        // Check if query is also present, e.g., "open free fire craftland in chrome"
        val isWebQuery = normalizedText.contains(" in chrome") || normalizedText.contains(" on chrome") ||
                normalizedText.contains(" chrome mein ") || normalizedText.contains(" chrome me ")
        if (isWebQuery) {
            val query = extractChromeQuery(normalizedText)
            if (query.isNotBlank()) {
                return ParsedCommand(
                    rawText = rawText,
                    normalizedText = normalizedText,
                    intent = IntentType.OPEN_PAGE,
                    targetApp = "Chrome",
                    query = query,
                    action = "OPEN_PAGE",
                    category = MessageCategory.NEW_COMMAND,
                    confidence = 1.0f
                )
            }
        }

        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.OPEN_APP,
            targetApp = targetApp,
            target = targetApp,
            action = "OPEN_APP",
            category = MessageCategory.NEW_COMMAND,
            confidence = 1.0f
        )
    }

    private fun encodeNavigate(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        val target = when {
            normalizedText.contains("settings") -> "settings"
            normalizedText.contains("home") -> "home"
            normalizedText.contains("back") -> "back"
            else -> "navigation"
        }
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.NAVIGATE,
            target = target,
            action = "NAVIGATE",
            category = MessageCategory.NEW_COMMAND,
            confidence = 1.0f
        )
    }

    private fun encodeMetaInstruction(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        val targetApp = detectTargetApp(normalizedText)
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.EXECUTION_PREFERENCE,
            targetApp = targetApp,
            preference = classification.preference ?: "assistant_should_perform_steps_itself",
            action = "SET_EXECUTION_PREFERENCE",
            category = MessageCategory.META_INSTRUCTION,
            confidence = 1.0f
            // CRITICAL: query is null! Never search!
        )
    }

    private fun encodeFailureFeedback(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.FAILURE_FEEDBACK,
            contextReference = classification.contextReference ?: ContextReference(ContextReferenceType.LAST_REQUEST),
            action = "RECORD_FAILURE_FEEDBACK",
            category = MessageCategory.FAILURE_FEEDBACK,
            confidence = 1.0f
            // CRITICAL: query is null! Never search!
        )
    }

    private fun encodeCorrection(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.CORRECTION,
            contextReference = classification.contextReference ?: ContextReference(ContextReferenceType.LAST_REQUEST),
            parameters = mapOf("correctionText" to rawText),
            action = "RECORD_CORRECTION",
            category = MessageCategory.CORRECTION,
            confidence = 1.0f
            // CRITICAL: query is null! Never search!
        )
    }

    private fun encodeStatusQuery(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = classification.intent,
            contextReference = classification.contextReference,
            action = "REPORT_EXECUTION_STATUS",
            category = MessageCategory.EXECUTION_STATUS,
            confidence = 1.0f
            // CRITICAL: query is null! Never search!
        )
    }

    private fun encodeRecallQuery(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.RECALL_REQUEST,
            contextReference = classification.contextReference,
            action = "RECALL_RECENT_REQUESTS",
            category = MessageCategory.CONTEXT_QUESTION,
            confidence = 1.0f
            // CRITICAL: query is null! Never search!
        )
    }

    private fun encodeApiKeyStatusQuery(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.API_KEY_STATUS_QUERY,
            action = "REPORT_API_KEY_STATUS",
            category = MessageCategory.API_KEY_STATUS_QUERY,
            confidence = 1.0f
        )
    }

    private fun encodeContextQuery(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        val target = classification.preference ?: "user_question"
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.CONTEXT_QUERY,
            contextReference = classification.contextReference,
            action = if (target == "assistant_response") "RECALL_ASSISTANT_MESSAGE" else "RECALL_USER_QUESTION",
            category = MessageCategory.CONTEXT_QUESTION,
            confidence = 1.0f
        )
    }

    private fun encodeWhyQuery(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.WHY_QUERY,
            contextReference = classification.contextReference,
            action = "EXPLAIN_PREVIOUS_EVENT",
            category = MessageCategory.WHY_QUESTION,
            confidence = 1.0f
        )
    }

    private fun encodeMetaConversation(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.META_CONVERSATION,
            action = "RESPOND_META_CONVERSATION",
            category = MessageCategory.META_CONVERSATION,
            confidence = 1.0f
        )
    }

    private fun encodeRetryRequest(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.RETRY_REQUEST,
            contextReference = classification.contextReference ?: ContextReference(ContextReferenceType.LAST_REQUEST),
            action = "RETRY_LAST_REQUEST",
            category = MessageCategory.RETRY_REQUEST,
            confidence = 1.0f
        )
    }

    private fun encodeCancelRequest(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.CANCEL_REQUEST,
            action = "CANCEL_ACTIVE_TASK",
            category = MessageCategory.CANCEL_REQUEST,
            confidence = 1.0f
        )
    }

    private fun encodeConfirmation(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.CONFIRMATION,
            action = "CONFIRM_ACTION",
            category = MessageCategory.CONFIRMATION,
            confidence = 1.0f
        )
    }

    private fun encodeDenial(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.DENIAL,
            action = "DENY_ACTION",
            category = MessageCategory.DENIAL,
            confidence = 1.0f
        )
    }

    private fun encodeMemoryQuery(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.MEMORY_QUERY,
            action = "RECALL_MEMORY",
            category = MessageCategory.MEMORY_QUERY,
            confidence = 1.0f
        )
    }

    private fun encodeGreeting(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.GREETING,
            action = "RESPOND_GREETING",
            category = MessageCategory.GREETING,
            confidence = 1.0f
        )
    }

    private fun encodeConversation(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.GENERAL_CONVERSATION,
            action = "RESPOND_CONVERSATION",
            category = MessageCategory.GENERAL_CONVERSATION,
            confidence = 1.0f
        )
    }

    private fun encodeQuestion(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.QUESTION,
            action = "ANSWER_QUESTION",
            category = MessageCategory.QUESTION,
            confidence = 1.0f
        )
    }

    private fun encodeChallenge(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = IntentType.CHALLENGE_REQUEST,
            action = "EXECUTE_CHALLENGE",
            category = MessageCategory.NEW_COMMAND,
            confidence = 1.0f
        )
    }

    private fun encodeGenericCommand(
        rawText: String,
        normalizedText: String,
        classification: IntentClassifier.ClassificationResult
    ): ParsedCommand {
        return ParsedCommand(
            rawText = rawText,
            normalizedText = normalizedText,
            intent = classification.intent,
            targetApp = detectTargetApp(normalizedText),
            category = classification.category,
            confidence = classification.confidence
        )
    }

    // -------------------------------------------------------------------------
    // Semantic Search Query Extraction
    // -------------------------------------------------------------------------

    /**
     * Extracts pure entity query for YouTube playback.
     * Example: "youtube par desi gamer ka video play kro" -> "desi gamer"
     * Example: "desi gamer ka video youtube par chala do" -> "desi gamer"
     * Example: "youtube pe desi gamer chalao" -> "desi gamer"
     */
    fun extractSearchAndPlayQuery(normalizedText: String): String {
        var text = normalizedText.lowercase().trim()

        // 1. Remove app prefixes / suffixes
        text = text.replace(Regex("(?i)^(?:kripya\\s+|please\\s+)?(?:youtube|yt)\\s+(?:par|pe|mein|me|se)?\\s*"), "")
        text = text.replace(Regex("(?i)\\s+(?:youtube|yt)\\s+(?:par|pe|mein|me)\\s*"), " ")
        text = text.replace(Regex("(?i)\\s+(?:ko|pe|par)\\s+(?:youtube|yt)\\s+(?:par|pe)?"), " ")
        text = text.replace(Regex("(?i)\\s+(?:on|in)\\s+youtube\\b"), "")
        text = text.replace(Regex("(?i)\\b(?:youtube|yt)\\b"), "")

        // 2. Remove play action suffixes / prefixes
        val actionSuffixes = listOf(
            Regex("(?i)\\s+(?:ka|ki|ke)?\\s*(?:video|gaana|song)?\\s*(?:play\\s+karo|play\\s+kar\\s+do|play\\s+kro|chala\\s+do|chalao|play|play\\s+karna)\\s*$"),
            Regex("(?i)\\s+(?:play\\s+karo|play\\s+kar\\s+do|play\\s+kro|chala\\s+do|chalao|play)\\s*$"),
            Regex("(?i)\\s+(?:प्ले\\s+करो|चलाओ|चला\\s+दो)\\s*$")
        )
        for (pattern in actionSuffixes) {
            text = pattern.replace(text, "")
        }

        // 3. Remove leading "play" / "chalao" if any
        text = text.replace(Regex("(?i)^(?:play|chalao|chala\\s+do)\\s+"), "")

        // 4. Remove lingering "ka video", "ki video", "video", "ka gaana"
        text = text.replace(Regex("(?i)\\s+(?:ka|ki|ke)?\\s*(?:video|song|gaana|गीत|वीडियो)\\s*$"), "")
        text = text.replace(Regex("(?i)^(?:video|song|gaana)\\s+"), "")

        // 5. Clean trailing/leading particles: "ka", "ki", "ke", "ko", "par", "pe"
        text = text.replace(Regex("(?i)\\s+(?:ka|ki|ke|ko|par|pe|mein|me)$"), "")
        text = text.replace(Regex("(?i)^(?:ka|ki|ke|ko|par|pe|mein|me)\\s+"), "")

        return text.trim()
    }

    /**
     * Extracts search query from general search commands.
     * Example: "google par free fire search karo" -> "free fire"
     * Example: "youtube par search karo tech burner" -> "tech burner"
     */
    fun extractGeneralSearchQuery(normalizedText: String): String {
        var text = normalizedText.lowercase().trim()

        // Remove app names
        text = text.replace(Regex("(?i)\\b(?:google|youtube|chrome|yt)\\s+(?:par|pe|mein|me)\\b"), "")
        text = text.replace(Regex("(?i)\\b(?:google|youtube|chrome|yt)\\b"), "")

        // Remove search action verbs
        text = text.replace(Regex("(?i)\\b(?:search\\s+karo|search\\s+kar\\s+do|search|khojo|dhoondo|खोजो|ढूंढो)\\b"), "")

        // Clean particles
        text = text.replace(Regex("(?i)\\s+(?:par|pe|mein|me|ko|karo|kar\\s+do)$"), "")
        text = text.replace(Regex("(?i)^(?:par|pe|mein|me|ko)\\s+"), "")

        return text.trim()
    }

    /**
     * Extracts web page query for Chrome.
     * Example: "open free fire craftland in chrome" -> "free fire craftland"
     * Example: "chrome mein free fire craftland kholo" -> "free fire craftland"
     */
    fun extractChromeQuery(normalizedText: String): String {
        var text = normalizedText.lowercase().trim()

        // "open free fire craftland in chrome"
        text = text.replace(Regex("(?i)^open\\s+"), "")
        text = text.replace(Regex("(?i)\\s+(?:in|on)\\s+chrome\\b.*"), "")

        // "chrome mein free fire craftland kholo"
        text = text.replace(Regex("(?i)^chrome\\s+(?:mein|me|pe|par)\\s+"), "")
        text = text.replace(Regex("(?i)\\s+kholo\\s*$"), "")
        text = text.replace(Regex("(?i)\\s+open\\s+karo\\s*$"), "")

        return text.trim()
    }

    private fun detectTargetApp(normalizedText: String): String? {
        val lower = normalizedText.lowercase()
        return when {
            lower.contains("youtube") || lower.contains("yt ") || lower.endsWith(" yt") -> "YouTube"
            lower.contains("chrome") -> "Chrome"
            lower.contains("google") -> "Google"
            lower.contains("whatsapp") -> "WhatsApp"
            lower.contains("settings") -> "Settings"
            lower.contains("camera") -> "Camera"
            lower.contains("spotify") -> "Spotify"
            lower.contains("instagram") -> "Instagram"
            else -> null
        }
    }

    private fun extractAppNameFromOpenCommand(normalizedText: String): String? {
        val match = Regex("(?i)(?:open|kholo|chalao)\\s+([a-zA-Z0-9]+)").find(normalizedText)
        return match?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }
    }

    private fun splitAndEncodeSubCommands(rawText: String, normalizedText: String): List<ParsedCommand> {
        val splitRegex = Regex("(?i)\\s+(aur|और|and|phir|फिर)\\s+")
        val parts = normalizedText.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
        val rawParts = rawText.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }

        val list = mutableListOf<ParsedCommand>()
        for (i in parts.indices) {
            val partNorm = parts[i]
            val partRaw = rawParts.getOrNull(i) ?: partNorm
            val classification = IntentClassifier.classify(partNorm, partRaw)
            val encoded = encode(partRaw, partNorm, classification)
            list.add(encoded)
        }
        return list
    }
}
