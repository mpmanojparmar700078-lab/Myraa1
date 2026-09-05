package com.example.services

import com.example.models.ConversationContextTracker
import com.example.models.IntentType
import com.example.models.LocalCommandResult
import com.example.models.ParsedIntent
import java.util.Locale

/**
 * Level 1 — Local Command Engine (V3.3 with Context & Intent Resolution)
 *
 * Evaluates user input deterministically in milliseconds without invoking Gemini API.
 * Normalizes Hindi, English, Hinglish, spaces, and conversational fillers.
 *
 * Supported Local Command Categories:
 * - Conversational Filler Cleaning ("theek hai", "arre bhai", "sun", "achha", "please", etc.)
 * - Context Queries ("kya tumhe pta hai maine kiski website open karne bola tha?", "maine kya search karne bola tha?")
 * - Pronoun & Context Resolution ("iski official website kholo" -> "Free Fire Craftland official website")
 * - Website Intent Priority (Prioritizes WEB_SEARCH / OPEN_URL over OPEN_APP for "website", "official website", "portal", etc.)
 * - Context Disambiguation Safety ("आप Free Fire या Minecraft में से किसकी website खोलना चाहते हैं?")
 * - Follow-up Task Continuation ("अब इसे play करो", "play भी करो", "ab play kro")
 * - Multi-Step Sequences ("YouTube खोलो और Free Fire search करो")
 * - YouTube Search & Play / Web Search / App Launches / Navigation / Preferences
 */
class LocalCommandParser {

    fun parse(rawInput: String): LocalCommandResult {
        // Track any discovered entities/topics from the user input in the context tracker
        ConversationContextTracker.recordDiscoveredTopic(rawInput)

        val normalized = normalizeInput(rawInput)
        if (normalized.isBlank()) {
            return LocalCommandResult(recognized = false, confidence = 0.0f, reason = "Empty input")
        }

        // Clean conversational fillers (preserving "ab" when followed by follow-up verbs)
        val cleanedInput = cleanConversationalFillers(normalized)
        val lower = cleanedInput.lowercase(Locale.getDefault())

        // 0. Cancellation / Stop / Pause / Resume
        val controlResult = parseCancellationOrControl(lower)
        if (controlResult != null) return controlResult

        // 1. Clear Conversation
        val clearChatResult = parseClearChat(lower)
        if (clearChatResult != null) return clearChatResult

        // 2. Greetings & Conversational Small Talk (Level 1 Deterministic - Instant 0ms response)
        val greetingResult = parseGreetings(lower)
        if (greetingResult != null) return greetingResult

        // 2b. Weather Queries (Level 1 Public API / Fast Weather Routing)
        val weatherResult = parseWeatherQuery(cleanedInput, lower)
        if (weatherResult != null) return weatherResult

        // 3. Language / Memory Preferences
        val preferenceResult = parsePreferences(cleanedInput, lower)
        if (preferenceResult != null) return preferenceResult

        // 4. Context Queries (e.g. "kya tumhe pta hai maine kiski website open karne bola tha?", "maine abhi kya search karne bola tha?")
        val contextQueryResult = parseContextQuery(cleanedInput, lower)
        if (contextQueryResult != null) return contextQueryResult

        // 5. Website / Link Commands with Pronoun or Entity Priority (e.g. "iski official website kholo", "Free Fire Craftland ki website kholo")
        val websiteResult = parseWebsiteIntent(cleanedInput, lower)
        if (websiteResult != null) return websiteResult

        // 6. Multi-Step Compound Commands (e.g., "Open YouTube and search for Free Fire")
        val multiStepResult = parseMultiStepCommands(cleanedInput, lower)
        if (multiStepResult != null) return multiStepResult

        // 7. YouTube Dedicated Search & Play (V3.3 Intelligent Video Play)
        val youtubeSearchPlayResult = parseYouTubeSearchAndPlay(cleanedInput, lower)
        if (youtubeSearchPlayResult != null) return youtubeSearchPlayResult

        // 7b. Follow-Up Task Continuation (V3.3 Context: "अब इसे play करो", "play भी करो", "ab play kro", "isko play kro")
        val followUpResult = parseFollowUpCommand(cleanedInput, lower)
        if (followUpResult != null) return followUpResult

        // 8. YouTube Dedicated Searches (e.g., "YouTube पर song search करो", "VK Bhuriya का song search करो YouTube पर")
        val youtubeSearchResult = parseYouTubeSearch(cleanedInput, lower)
        if (youtubeSearchResult != null) return youtubeSearchResult

        // 9. Google / Web Searches (e.g., "Google पर Free Fire search करो", "Search Free Fire on Google")
        val webSearchResult = parseWebSearch(cleanedInput, lower)
        if (webSearchResult != null) return webSearchResult

        // 10. Navigation (Back / Home)
        val navigationResult = parseNavigation(lower)
        if (navigationResult != null) return navigationResult

        // 11. URLs ("https://...", "open www...")
        val urlResult = parseUrl(cleanedInput, lower)
        if (urlResult != null) return urlResult

        // 12. Named Apps & Generic App Launches ("YouTube खोलो", "Open Chrome", "Settings खोलो", etc.)
        val appResult = parseAppLaunch(cleanedInput, lower)
        if (appResult != null) return appResult

        // Not recognized by local engine -> Will be delegated to Gemini Level 2 Engine
        return LocalCommandResult(
            recognized = false,
            confidence = 0.0f,
            reason = "Command requires Gemini AI reasoning or conversational engine"
        )
    }

    /**
     * Normalizes text by removing extra spaces, leading/trailing punctuation, and standardizing common characters.
     */
    private fun normalizeInput(input: String): String {
        var clean = input.trim()
        clean = clean.replace(Regex("""[?!.,;।|]+$"""), "").trim()
        clean = clean.replace(Regex("""\s+"""), " ")
        return clean
    }

    /**
     * Cleans conversational prefixes and fillers before intent classification.
     * Fillers: "theek hai", "arre", "bhai", "sun", "achha", "ok", "okay", "please", "pls", "plz", "yaar", "dekho", "think rum", etc.
     * Note: "ab" / "अब" is preserved if it is part of a follow-up ("ab play kro", "ab ise chalao").
     */
    private fun cleanConversationalFillers(input: String): String {
        var text = input.trim()

        // List of conversational prefixes to clean from the start of the sentence
        val prefixPatterns = listOf(
            Regex("""^(?:theek\s+hai|theek\s+h|thik\s+hai|thik\s+h|ठीक\s+है)\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(?:arre\s+bhai|arre\s+yaar|arre|अरे\s+भाई|अरे\s+यार|अरे)\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(?:think\s+rum|think\s+ram|think\s+hum)\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(?:bhai|भाई|sun\s+bhai|sun|सुन\s+भाई|सुन|achha|अच्छा|accha)\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(?:ok|okay|k|ओक|ओके)\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(?:please|pls|plz|कृपया)\s+""", RegexOption.IGNORE_CASE),
            Regex("""^(?:yaar|यार|dekho|देखो|suno|सुनो)\s+""", RegexOption.IGNORE_CASE)
        )

        var changed = true
        while (changed) {
            changed = false
            for (pattern in prefixPatterns) {
                if (pattern.containsMatchIn(text)) {
                    text = text.replaceFirst(pattern, "").trim()
                    changed = true
                }
            }
        }

        // Handle "ab" / "अब": Only strip if followed by non-continuation commands (e.g. "ab Free Fire search kro")
        // but keep if followed by "play", "ise", "isko", "chalao", "kholo", etc.
        val abPrefix = Regex("""^(?:ab|अब)\s+""", RegexOption.IGNORE_CASE)
        if (abPrefix.containsMatchIn(text)) {
            val rest = text.replaceFirst(abPrefix, "").trim().lowercase(Locale.ROOT)
            val isFollowUpRest = rest.startsWith("play") || rest.startsWith("ise") || rest.startsWith("isko") ||
                    rest.startsWith("इसे") || rest.startsWith("इसको") || rest.startsWith("chalao") ||
                    rest.startsWith("चलाओ") || rest.startsWith("video") || rest.startsWith("वीडियो")
            if (!isFollowUpRest && rest.contains("search") || rest.contains("खोलो") || rest.contains("open")) {
                text = text.replaceFirst(abPrefix, "").trim()
            }
        }

        return text
    }

    /**
     * Detects when the user is asking Myra about previous conversation context.
     * Examples:
     * - "kya tumhe pta hai maine kiski website open karne bola tha?"
     * - "maine abhi kya search karne bola tha?"
     * - "maine kiski website puchi thi?"
     * - "what did I ask you to search?"
     * - "what website did I ask you to open?"
     * - "maine kya bola tha?"
     */
    private fun parseContextQuery(original: String, lower: String): LocalCommandResult? {
        val isContextQuestion = lower.contains("maine") || lower.contains("mene") || lower.contains(" मैंने") ||
                lower.contains("meme ") || lower.contains("meme aage") || lower.contains("meme pichli") ||
                lower.contains("what did i") || lower.contains("did i ask") || lower.contains("kya bola tha") ||
                lower.contains("kya pucha tha") || lower.contains("kya kaha tha") || lower.contains("kya search karne bola") ||
                lower.contains("kiski website") || lower.contains("pichli chat") || lower.contains("aage wali chat") ||
                lower.contains("purani chat") || lower.contains("chat history")

        if (!isContextQuestion) return null

        val askingAboutWebsite = lower.contains("website") || lower.contains("web site") ||
                lower.contains("site") || lower.contains("वेबसाइट") || lower.contains("साइट")

        val askingAboutSearchOrQuery = lower.contains("search") || lower.contains("सर्च") ||
                lower.contains("खोज") || lower.contains("play") || lower.contains("चलाने")

        val isDirectContextQuery = (lower.contains("kya tumhe pta hai") || lower.contains("kya tumhe pata hai") ||
                lower.contains("kya apko pata hai") || lower.contains("kya aapko pata hai") ||
                lower.contains("kya yaad hai") || lower.contains("do you know") || lower.contains("remember") ||
                lower.contains("maine kiski") || lower.contains("mene kiski") || lower.contains("maine kya") ||
                lower.contains("mene kya") || lower.contains("meme kya") || lower.contains("meme aage") ||
                lower.contains("maine abhi kya") || lower.contains("aage wali chat") || lower.contains("pichli chat"))

        if (!isDirectContextQuery && !askingAboutWebsite && !askingAboutSearchOrQuery) {
            return null
        }

        val context = ConversationContextTracker.getActiveContext()

        if (askingAboutWebsite) {
            val websiteEntity = context?.lastWebsiteEntity ?: context?.effectiveEntity
            val replyText = if (websiteEntity != null) {
                "आपने $websiteEntity की official website खोलने को कहा था।"
            } else {
                "मुझे थोड़ा याद नहीं आ रहा है कि आपने किसकी website खोलने को कहा था। क्या आप फिर से बता सकते हैं? मैं अभी ओपन कर दूँगी!"
            }
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = replyText
                ),
                confidence = 1.0f,
                reason = "Resolved context query regarding website from local context"
            )
        }

        if (askingAboutSearchOrQuery || isDirectContextQuery) {
            val pastQuery = context?.query ?: context?.effectiveEntity
            val replyText = if (pastQuery != null) {
                if (context?.platform.equals("YOUTUBE", ignoreCase = true)) {
                    "हाँ, आपने YouTube पर '$pastQuery' खोजने/चलाने को कहा था।"
                } else {
                    "हाँ, आपने '$pastQuery' सर्च करने को कहा था।"
                }
            } else {
                "मुझे अभी याद नहीं आ रहा है कि आपने क्या सर्च करने को कहा था। क्या आप फिर से बता सकते हैं?"
            }
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = replyText
                ),
                confidence = 1.0f,
                reason = "Resolved general context query from local context"
            )
        }

        return null
    }

    /**
     * Dedicated Website & Link intent parser with Pronoun & Entity Resolution.
     * Prioritizes WEB_SEARCH / OPEN_URL over OPEN_APP for commands with "website", "official website", "site", etc.
     *
     * Handles:
     * - "Free Fire Craftland ki official website kholo" -> WEB_SEARCH "Free Fire Craftland official website"
     * - "Free Fire ki official website search kro" -> WEB_SEARCH "Free Fire official website"
     * - "iski official website kholo" -> Resolves pronoun using context.effectiveEntity
     * - "iski website search kro" / "website kholo" / "open official website"
     * - Ambiguity check: "आप Free Fire या Minecraft में से किसकी website खोलना चाहते हैं?"
     */
    private fun parseWebsiteIntent(original: String, lower: String): LocalCommandResult? {
        val isWebsiteKeyword = lower.contains("website") || lower.contains("web site") ||
                lower.contains("official website") || lower.contains("webpage") || lower.contains("homepage") ||
                lower.contains("portal") || lower.contains("वेबसाइट") || lower.contains("ऑफिशियल वेबसाइट") ||
                lower.contains("ऑफिसियल वेबसाइट") || lower.contains("वेबपेज") || lower.contains("होमपेज") ||
                (lower.contains(" site") || lower.startsWith("site ") || lower.contains("साइट")) ||
                (lower.contains(" link") || lower.startsWith("link ") || lower.contains("लिंक"))

        if (!isWebsiteKeyword) return null

        // Check pronoun reference: "iski", "iska", "iske", "is", "isko", "use", "usko", "uska", "uski", "it", "this", "that", "its"
        val pronounPattern = Regex("""\b(?:iski|iska|iske|is|isko|use|usko|uska|uski|isse|isse|it|this|that|its|इसकी|इसका|इसके|इसको|उसका|उसकी|उसके|उसको|इसे)\b""", RegexOption.IGNORE_CASE)
        val hasPronoun = pronounPattern.containsMatchIn(lower)

        val context = ConversationContextTracker.getActiveContext()

        // 1. Check for ambiguous entities first
        if (hasPronoun && context != null && context.isAmbiguousEntity) {
            val candidateNames = context.candidateEntities.joinToString(" या ")
            val questionText = "आप $candidateNames में से किसकी website खोलना चाहते हैं?"
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = questionText
                ),
                confidence = 1.0f,
                reason = "Ambiguous entities detected for pronoun resolution; asking clarification"
            )
        }

        // 2. Extract explicitly mentioned entity in the command
        var extractedEntity: String? = null

        // Pattern A: "[Entity] ki/ka/ke official website / website kholo / search kro"
        val explicitWebsiteRegexHi = Regex("""^(?:open\s+|search\s+|google\s+par\s+)?(.+?)\s*(?:ki|ka|ke|की|का|के)?\s*(?:official\s+website|website|web\s+site|site|webpage|homepage|portal|ऑफिशियल\s+वेबसाइट|ऑफिसियल\s+वेबसाइट|वेबसाइट|साइट|लिंक|link)\s*(?:search\s+kro|search\s+karo|search|सर्च|खोजो|ढूंढो)?\s*(?:kholo|open\s+kro|open\s+karo|open|karo|kr\s+do|करो|खोलो|ओपन\s+करो)?$""", RegexOption.IGNORE_CASE)
        val mHi = explicitWebsiteRegexHi.find(original)
        if (mHi != null) {
            val candidate = mHi.groupValues[1].trim()
            if (candidate.isNotBlank() && !pronounPattern.matches(candidate) && !isGenericStopWord(candidate)) {
                extractedEntity = candidate
            }
        }

        // Pattern B: "open [Entity] (official) website"
        val explicitWebsiteRegexEn = Regex("""^(?:open|search|find)\s+(?:the\s+)?(?:official\s+)?(?:website\s+of\s+|site\s+of\s+)?(.+?)\s*(?:official\s+website|website|site|portal|webpage|homepage)?$""", RegexOption.IGNORE_CASE)
        val mEn = explicitWebsiteRegexEn.find(original)
        if (extractedEntity == null && mEn != null) {
            val candidate = mEn.groupValues[1].trim()
            if (candidate.isNotBlank() && !pronounPattern.matches(candidate) && !isGenericStopWord(candidate)) {
                extractedEntity = candidate
            }
        }

        // 3. Resolve target entity
        val finalEntity: String? = when {
            extractedEntity != null && extractedEntity.isNotBlank() -> cleanEntityName(extractedEntity)
            hasPronoun -> context?.effectiveEntity
            else -> {
                // If user just says "official website open kro" / "website kholo"
                val stripped = original.replace(Regex("""(?i)\b(?:open|search|kholo|kro|karo|khojo|the|ki|ka|ke|official|website|web\s+site|site|लिंक|link|वेबसाइट|ऑफिशियल)\b"""), "").trim()
                if (stripped.isNotBlank() && !pronounPattern.matches(stripped)) {
                    cleanEntityName(stripped)
                } else {
                    context?.effectiveEntity
                }
            }
        }

        if (finalEntity.isNullOrBlank()) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = "आप किसकी official website खोलना चाहते हैं? कृपया नाम बताएं।"
                ),
                confidence = 1.0f,
                reason = "Website command without identifiable entity"
            )
        }

        val targetSearchQuery = "$finalEntity official website"

        // Update context with the resolved website target and entity
        ConversationContextTracker.updateContext(
            platform = "GOOGLE",
            query = targetSearchQuery,
            lastAction = "OPEN_WEBSITE",
            lastIntent = IntentType.WEB_SEARCH,
            activeTaskApp = "Chrome",
            activeTopicEntity = finalEntity,
            lastEntity = finalEntity,
            lastWebsiteTarget = targetSearchQuery,
            lastWebsiteEntity = finalEntity
        )

        return LocalCommandResult(
            recognized = true,
            intent = IntentType.WEB_SEARCH,
            parsedIntent = ParsedIntent(
                type = IntentType.WEB_SEARCH,
                query = targetSearchQuery,
                responseText = "Google पर '$targetSearchQuery' खोजा जा रहा है…"
            ),
            parameters = mapOf("query" to targetSearchQuery, "resolvedEntity" to finalEntity),
            confidence = 1.0f,
            reason = "Resolved website search intent for entity: $finalEntity"
        )
    }

    private fun cleanEntityName(raw: String): String {
        var clean = raw.trim()
        clean = clean.replace(Regex("""^(?:theek\s+hai|arre|bhai|sun|achha|ok|okay|please|pls|plz|yaar|dekho|think\s+rum)\s+""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""^(?:mujhe|hume|mujhko|please|can\s+you)\s+""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""^(?:open|search|kholo|find|गूगल\s+पर|google\s+par)\s+""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""\s*(?:ki|ka|ke|की|का|के)$""", RegexOption.IGNORE_CASE), "")
        return clean.trim()
    }

    private fun isGenericStopWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT).trim()
        val stopWords = setOf("kuch", "kya", "yeh", "woh", "this", "that", "it", "something", "anything", "iski", "iska", "iske", "isko", "इसे", "इसकी", "इसका")
        return stopWords.contains(lower)
    }


    private fun parseCancellationOrControl(lower: String): LocalCommandResult? {
        val cancelTriggers = setOf(
            "stop", "cancel", "रुको", "रुक जाओ", "काम बंद करो", "बस", "बंद करो",
            "ruk jao", "ruko", "band karo", "stop it", "cancel task", "stop task"
        )
        if (cancelTriggers.contains(lower)) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.BACK,
                parsedIntent = ParsedIntent(
                    type = IntentType.BACK,
                    responseText = "कार्य रोक दिया गया है। (Task stopped)"
                ),
                confidence = 1.0f,
                reason = "Cancellation / Stop command matched"
            )
        }
        return null
    }

    private fun parseClearChat(lower: String): LocalCommandResult? {
        val clearKeywords = listOf(
            "clear chat", "clear conversation", "clear history", "delete chat", "reset chat",
            "चैट मिटाओ", "चैट साफ करो", "चैट क्लियर करो", "हिस्ट्री मिटाओ", "चैट डिलीट करो",
            "chat clear", "clear"
        )
        if (clearKeywords.any { lower == it || lower == "$it please" }) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.CLEAR_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.CLEAR_CHAT,
                    responseText = "चैट हिस्ट्री साफ़ कर दी गई है। (Chat history cleared)"
                ),
                confidence = 1.0f,
                reason = "Local clear chat command matched"
            )
        }
        return null
    }

    private fun parsePreferences(original: String, lower: String): LocalCommandResult? {
        if (lower.contains("हमेशा हिंदी में") || lower.contains("हिंदी में बात") ||
            (lower.contains("talk") && lower.contains("hindi")) || (lower.contains("speak") && lower.contains("hindi")) ||
            lower.contains("hindi me baat karo") || lower.contains("hindi me bolo")) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.SET_PREFERENCE,
                parsedIntent = ParsedIntent(
                    type = IntentType.SET_PREFERENCE,
                    key = "preferred_language",
                    value = "hi",
                    responseText = "ठीक है, मैंने याद रख लिया है। अब से मैं आपसे हमेशा हिंदी में बात करूँगी।"
                ),
                parameters = mapOf("key" to "preferred_language", "value" to "hi"),
                confidence = 1.0f,
                reason = "Local preference match: Hindi"
            )
        }

        if (lower.contains("talk in english") || lower.contains("speak in english") ||
            lower.contains("always talk in english") || lower.contains("english me baat karo") ||
            lower.contains("हमेशा अंग्रेजी में") || lower.contains("इंग्लिश में बात")) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.SET_PREFERENCE,
                parsedIntent = ParsedIntent(
                    type = IntentType.SET_PREFERENCE,
                    key = "preferred_language",
                    value = "en",
                    responseText = "Understood! I have remembered your preference. I will communicate with you in English."
                ),
                parameters = mapOf("key" to "preferred_language", "value" to "en"),
                confidence = 1.0f,
                reason = "Local preference match: English"
            )
        }

        return null
    }

    private fun parseGreetings(lower: String): LocalCommandResult? {
        val trimmed = lower.trim().replace(Regex("""[!.?,~]+$"""), "").trim()

        // 1. Repetitive hi/hey/hello regex variations (e.g., "hii", "hiii", "heyy", "helloo", "hlo", "hlw", "hyy")
        val isHiVariation = trimmed.matches(Regex("""^h+i+[i!]*$""")) || // hi, hii, hiii, hiiii
                trimmed.matches(Regex("""^h+e+y+[y!]*$""")) || // hey, heyy, heyyy
                trimmed.matches(Regex("""^h+e+l+l*o+[o!]*$""")) || // hello, helloo, helo, hellooo
                trimmed.matches(Regex("""^h+l+o+[o!]*$""")) || // hlo, hloo
                trimmed.matches(Regex("""^h+l+w+[w!]*$""")) || // hlw, hlww
                trimmed.matches(Regex("""^h+y+[y!]*$""")) || // hy, hyy, hyyy
                trimmed.matches(Regex("""^yo+[o!]*$""")) || // yo, yoo
                trimmed.matches(Regex("""^hola+[a!]*$""")) // hola

        // 2. Direct greeting triggers
        val greetingTriggers = setOf(
            "hello", "hi", "hey", "hello myra", "hi myra", "hey myra", "hiii", "hii", "heyy",
            "नमस्ते", "नमस्ते मायरा", "नमस्कार", "namaste", "namaste myra", "good morning", "good evening",
            "good afternoon", "good night", "shubh ratri", "shubh prabhat", "सुप्रभात", "शुभ रात्रि",
            "राम राम", "जय श्री राम", "राधे राधे", "radhe radhe", "ram ram", "jai shree ram"
        )

        // 3. Well-being triggers
        val wellbeingTriggers = setOf(
            "kaise ho", "kese ho", "kaise ho myra", "kese ho myra", "kya haal hai", "kya haal h",
            "kya chal raha hai", "how are you", "how are you myra", "how r u", "whats up", "what's up",
            "wassup", "sup", "sab theek", "sab badiya", "all good"
        )

        // 4. Identity / Capabilities triggers
        val identityTriggers = setOf(
            "who are you", "who are you myra", "who r u", "tum kaun ho", "aap kaun ho", "ap kon ho",
            "tum kon ho", "myra kaun hai", "myra kon hai", "what can you do", "kya kar sakti ho",
            "tum kya kar sakti ho", "aap kya kar sakti ho", "introduce yourself", "apna intro do"
        )

        // 5. Thanks / Appreciation triggers
        val thanksTriggers = setOf(
            "thank you", "thanks", "dhanyawad", "shukriya", "thank u", "thx", "thank you myra",
            "धन्यवाद", "शुक्रिया"
        )

        val isGreeting = isHiVariation || greetingTriggers.contains(trimmed) ||
                trimmed.startsWith("hi myra") || trimmed.startsWith("hello myra") || trimmed.startsWith("hey myra") ||
                trimmed.startsWith("namaste myra") || trimmed.startsWith("नमस्ते मायरा")

        if (isGreeting) {
            val reply = if (trimmed.contains("नमस्ते") || trimmed.contains("नमस्कार") || trimmed.contains("namaste") || trimmed.contains("राम") || trimmed.contains("राधे")) {
                "नमस्ते! मैं Myra हूँ, आपकी निजी AI असिस्टेंट। मैं ऐप्स खोल सकती हूँ, YouTube पर वीडियो खोज और चला सकती हूँ, और वेब सर्च कर सकती हूँ। बताइए, आज क्या मदद करूँ?"
            } else {
                "Hello! I am Myra, your personal AI assistant. I can open apps, search and play YouTube videos, and search the web for you. How can I help you today?"
            }
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = reply
                ),
                confidence = 1.0f,
                reason = "Local greeting match: $trimmed"
            )
        }

        if (wellbeingTriggers.contains(trimmed) || trimmed.contains("how are you") || trimmed.contains("kaise ho") || trimmed.contains("kya haal")) {
            val reply = if (trimmed.contains("kaise") || trimmed.contains("kese") || trimmed.contains("kya haal") || trimmed.contains("sab theek")) {
                "मैं बिल्कुल ठीक हूँ! आपकी क्या मदद करूँ? आप कोई ऐप खोलने, YouTube वीडियो चलाने या सर्च करने के लिए कह सकते हैं।"
            } else {
                "I'm doing great! How can I help you today? You can ask me to open apps, play YouTube videos, or search the web."
            }
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = reply
                ),
                confidence = 1.0f,
                reason = "Local well-being match: $trimmed"
            )
        }

        if (identityTriggers.contains(trimmed) || trimmed.contains("who are you") || trimmed.contains("tum kaun ho") || trimmed.contains("aap kaun ho")) {
            val reply = if (trimmed.contains("tum") || trimmed.contains("aap") || trimmed.contains("kon") || trimmed.contains("kaun") || trimmed.contains("kya kar")) {
                "मैं Myra हूँ — आपकी Android AI असिस्टेंट। मैं आपके फ़ोन में YouTube पर गाने या वीडियो चला सकती हूँ, कोई भी ऐप (जैसे WhatsApp, Camera, Chrome, Settings) खोल सकती हूँ और Google सर्च कर सकती हूँ।"
            } else {
                "I am Myra — your Android AI assistant. I can open apps, search and play YouTube videos, and search the web for you."
            }
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = reply
                ),
                confidence = 1.0f,
                reason = "Local identity match: $trimmed"
            )
        }

        if (thanksTriggers.contains(trimmed)) {
            val reply = if (trimmed.contains("dhanyawad") || trimmed.contains("shukriya") || trimmed.contains("धन्यवाद") || trimmed.contains("शुक्रिया")) {
                "आपका स्वागत है! अगर कोई और काम हो तो जरूर बताएं।"
            } else {
                "You're welcome! Let me know if there's anything else I can do for you."
            }
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = reply
                ),
                confidence = 1.0f,
                reason = "Local appreciation match: $trimmed"
            )
        }

        return null
    }

    /**
     * Dedicated Weather intent parser (Level 1 Fast Public API Routing).
     * Handles queries like:
     * - "Aaj ka hmari current location ka weather btao"
     * - "Weather kaisa hai"
     * - "Mausam kaisa hai"
     * - "What's the weather today"
     * - "Weather in Mumbai"
     */
    private fun parseWeatherQuery(original: String, lower: String): LocalCommandResult? {
        // If user explicitly asks Google / Web search, or asks for songs/videos/recommendations, do NOT intercept
        val isExplicitSearch = lower.contains("google") || lower.contains("गूगल") ||
                lower.startsWith("search ") || lower.contains(" search करो") ||
                lower.contains(" search kro") || lower.contains(" search karo")
        if (isExplicitSearch) return null

        val isSongOrCreative = lower.contains("गाना") || lower.contains("gaana") || lower.contains("song") ||
                lower.contains("video") || lower.contains("वीडियो") || lower.contains("ढूंढो") ||
                lower.contains("suno") || lower.contains("सुनो") || lower.contains("सुनाओ")
        if (isSongOrCreative) return null

        val isWeatherMention = lower.contains("weather") || lower.contains("मौसम") ||
                lower.contains("mausam") || lower.contains("तापमान") || lower.contains("temperature")
        if (!isWeatherMention) return null

        // Must look like an inquiry about current/forecast weather
        val isWeatherInquiry = lower.contains("kaisa") || lower.contains("कैसा") || lower.contains("kese") ||
                lower.contains("btao") || lower.contains("batao") || lower.contains("बताओ") ||
                lower.contains("today") || lower.contains("aaj") || lower.contains("आज") ||
                lower.contains("current") || lower.contains("report") || lower.contains("forecast") ||
                lower.contains("in ") || lower.contains("mein") || lower.contains("में") ||
                lower.contains("par") || lower.contains("का") || lower.contains("ka") ||
                lower.trim() == "weather" || lower.trim() == "मौसम" || lower.trim() == "mausam"
        if (!isWeatherInquiry) return null

        // Check if a specific city is explicitly mentioned (e.g., "weather in Delhi", "mumbai ka mausam")
        val cityPatterns = listOf(
            Regex("""(?:weather\s+in|weather\s+of)\s+([a-zA-Z\u0900-\u097F]+)""", RegexOption.IGNORE_CASE),
            Regex("""([a-zA-Z\u0900-\u097F]+)\s*(?:ka\s+mausam|का\s+मौसम)""", RegexOption.IGNORE_CASE),
            Regex("""([a-zA-Z\u0900-\u097F]+)\s+weather""", RegexOption.IGNORE_CASE)
        )

        var detectedCity: String? = null
        val nonCityWords = setOf(
            "aaj", "today", "current", "location", "hamari", "hmari", "mera", "yaha", "yahan",
            "kaisa", "hai", "btao", "batao", "report", "ki", "ka", "me", "mein", "par", "the"
        )
        for (pattern in cityPatterns) {
            val match = pattern.find(lower)
            if (match != null && match.groupValues.size > 1) {
                val candidate = match.groupValues[1].trim()
                if (candidate !in nonCityWords) {
                    detectedCity = candidate.replaceFirstChar { it.uppercase() }
                    break
                }
            }
        }

        val targetApiId = if (detectedCity != null) "wttr_in" else "open_meteo"
        val params = if (detectedCity != null) mapOf("location" to detectedCity) else emptyMap()
        val responseIntro = if (detectedCity != null) "$detectedCity का मौसम जाँचा जा रहा है…" else "वर्तमान स्थान का मौसम जाँचा जा रहा है…"

        return LocalCommandResult(
            recognized = true,
            intent = IntentType.PUBLIC_API,
            parsedIntent = ParsedIntent(
                type = IntentType.PUBLIC_API,
                query = original,
                apiId = targetApiId,
                apiParams = params,
                responseText = responseIntro
            ),
            parameters = params,
            confidence = 1.0f,
            reason = "Fast local weather routing to $targetApiId"
        )
    }

    private fun parseNavigation(lower: String): LocalCommandResult? {
        val backCommands = setOf("back", "go back", "पीछे जाओ", "वापस जाओ", "back जाओ", "back karo", "home", "go home", "होम जाओ", "होम स्क्रीन")
        if (backCommands.contains(lower)) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.BACK,
                parsedIntent = ParsedIntent(
                    type = IntentType.BACK,
                    responseText = "Going back to home screen."
                ),
                confidence = 1.0f,
                reason = "Local navigation command matched"
            )
        }
        return null
    }

    /**
     * Dedicated YouTube Search & Play parser (V3.3 Intelligent Result Control).
     * Recognizes commands where user explicitly asks to search AND play/watch a video:
     * - "YouTube पर VK Bhuriya का song search करो और relevant video play करो"
     * - "YouTube पर VK Bhuriya का song search करो और play करो"
     * - "YouTube पर VK Bhuriya का song खोजो और चलाओ"
     * - "YouTube par VK Bhuriya song search karo aur play karo"
     * - "YouTube par VK Bhuriya song search karo aur chalao"
     * - "YouTube पर motivational song search करो और relevant video चलाओ"
     * - "Search VK Bhuriya song on YouTube and play the relevant video"
     * - "Search VK Bhuriya song on YouTube and play it"
     * - "Find VK Bhuriya song on YouTube and play it"
     * - "YouTube पर VK Bhuriya song play करो" / "YouTube par VK Bhuriya song chalao"
     * - "Play VK Bhuriya song on YouTube"
     */
    private fun parseYouTubeSearchAndPlay(original: String, lower: String): LocalCommandResult? {
        val isYtMentioned = lower.contains("youtube") || lower.contains("यूट्यूब")
        if (!isYtMentioned) return null

        val isPlayCommand = lower.contains("play") || lower.contains("चलाओ") || lower.contains("चला दो") ||
                lower.contains("chalao") || lower.contains("bajao") || lower.contains("बजाओ") ||
                lower.contains("watch") || lower.contains("dekho") || lower.contains("देखें") ||
                lower.contains("relevant video")
        if (!isPlayCommand) return null

        // Avoid decomposing multi-step compound commands that were handled in parseMultiStepCommands
        if (lower.contains("खोलो और") || lower.contains("open and") || lower.contains("launch and")) {
            return null
        }

        // 1. English: "Search/Find [query] on YouTube and play (the relevant video / it)"
        val enSearchPlayRegex = Regex("""^(?:search|find)\s+(?:for\s+|youtube\s+for\s+)?(.+?)\s+(?:on|in)\s+youtube\s+(?:and|then)\s+(?:play|watch)(?:\s+(?:the\s+)?relevant\s+video|\s+it)?$""", RegexOption.IGNORE_CASE)
        val mEn = enSearchPlayRegex.find(original)
        if (mEn != null) {
            val rawQuery = mEn.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchAndPlayResult(cleanedQuery)
            }
        }

        // 2. English: "Play [query] on YouTube" / "Play [query] in YouTube"
        val enPlayRegex = Regex("""^play\s+(.+?)\s+(?:on|in)\s+youtube$""", RegexOption.IGNORE_CASE)
        val mEnPlay = enPlayRegex.find(original)
        if (mEnPlay != null) {
            val rawQuery = mEnPlay.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchAndPlayResult(cleanedQuery)
            }
        }

        // 3. Hindi/Hinglish prefix: "YouTube पर [query] search/खोजो करो और play/चलाओ करो"
        val hiPrefixPlayRegex = Regex("""^(?:youtube|यूट्यूब)\s*(?:पर|par|में|me|pe|पे)?\s*(.+?)\s*(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find)?\s*(?:करो|kro|karo)?\s*(?:और|aur|फिर|then)\s*(?:relevant\s+video\s+|उपयुक्त\s+वीडियो\s+)?(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao|watch|dekho|देखें|चलाना)(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val mHiPrefix = hiPrefixPlayRegex.find(original)
        if (mHiPrefix != null) {
            val rawQuery = mHiPrefix.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchAndPlayResult(cleanedQuery)
            }
        }

        // 4. Hindi/Hinglish direct play: "YouTube पर [query] play करो / चलाओ"
        val hiDirectPlayRegex = Regex("""^(?:youtube|यूट्यूब)\s*(?:पर|par|में|me|pe|पे)?\s*(.+?)\s+(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao|watch|dekho|देखें|चलाना)(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val mHiDirect = hiDirectPlayRegex.find(original)
        if (mHiDirect != null) {
            val rawQuery = mHiDirect.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchAndPlayResult(cleanedQuery)
            }
        }

        // 5. Hindi/Hinglish suffix: "[query] YouTube पर search करो और play करो" / "[query] search karo youtube par aur play karo"
        val hiSuffixPlayRegex = Regex("""^(.+?)\s+(?:youtube|यूट्यूब)\s*(?:पर|par|में|me|pe|पे)\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find)?\s*(?:करो|kro|karo)?\s*(?:और|aur|फिर|then)\s*(?:relevant\s+video\s+|उपयुक्त\s+वीडियो\s+)?(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao|watch|dekho|देखें)(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val mHiSuffix = hiSuffixPlayRegex.find(original)
        if (mHiSuffix != null) {
            val rawQuery = mHiSuffix.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchAndPlayResult(cleanedQuery)
            }
        }

        // 6. Hindi/Hinglish suffix direct play: "[query] चलाओ YouTube पर" / "[query] play karo youtube par"
        val hiSuffixDirectPlayRegex = Regex("""^(.+?)\s+(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao)(?:\s*(?:करो|kro|karo|do|दो))?\s+(?:on\s+youtube|in\s+youtube|youtube\s*पर|youtube\s*par|यूट्यूब\s*पर|youtube\s*me|youtube\s*pe)$""", RegexOption.IGNORE_CASE)
        val mHiSuffixDirect = hiSuffixDirectPlayRegex.find(original)
        if (mHiSuffixDirect != null) {
            val rawQuery = mHiSuffixDirect.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchAndPlayResult(cleanedQuery)
            }
        }

        return null
    }

    private val FOLLOW_UP_EXACT_PHRASES = setOf(
        "अब play करो", "play भी करो", "अब इसे play करो", "इसे चलाओ", "वीडियो चलाओ", "उस video को चलाओ",
        "search करके play करो", "search करके play भी करो", "open it", "play it", "play this",
        "isko chalao", "ab play kro", "play bhi kro", "ise play karo", "ise play kro", "ise chalao",
        "us video ko chalao", "chalao", "play karo", "play kro", "search karke play karo",
        "search karke play bhi karo", "ab ise play karo", "ab ise play kro", "ab play karo",
        "play it now", "play the video", "start it", "video chalao", "isko play karo", "isko play kro",
        "ise bhi chalao", "play bhi karo", "use play karo", "use chalao", "ise play kr do", "play kar do",
        "ab chalao", "ab video chalao", "video play karo", "video play kro", "ab play kar do", "ab ise chala do",
        "chala do", "ise bajao", "bajao", "ab bajao"
    )

    private fun isFollowUpPhrase(lower: String): Boolean {
        val clean = lower.trim().replace(Regex("""[?!.,;।|]+$"""), "").trim()
        if (FOLLOW_UP_EXACT_PHRASES.contains(clean)) return true
        if (ReferenceResolver.extractPositionalIndex(clean) != null) return true

        // Follow-up pattern A: "search karke play (bhi) karo"
        if (Regex("""^(?:search|सर्च)\s*(?:करके|karke)\s*(?:play|चलाओ|chalao|बजाओ|bajao)\s*(?:भी\s+|bhi\s+)?(?:करो|kro|karo|do|दो)?$""", RegexOption.IGNORE_CASE).matches(clean)) {
            return true
        }

        // Follow-up pattern B: "[ab/अब] [ise/isko/use/इसे/उसको/वीडियो] [bhi/भी] [play/chalao/चलाओ/bajao/बजाओ] [karo/kro/do/दो]"
        if (Regex("""^(?:अब\s+|ab\s+)?(?:इसे|isko|ise|use|इसको|उस\s*video\s*को|इस\s*वीडियो\s*को|video|वीडियो)?\s*(?:भी\s+|bhi\s+)?(?:play|चलाओ|chalao|बजाओ|bajao)(?:\s*(?:करो|kro|karo|do|दो|it|this))?$""", RegexOption.IGNORE_CASE).matches(clean)) {
            return true
        }

        // Follow-up pattern C: "open/play/start it/this"
        if (Regex("""^(?:open|play|start)\s+(?:it|this|the\s+video)(?:\s+now|\s+please)?$""", RegexOption.IGNORE_CASE).matches(clean)) {
            return true
        }

        return false
    }

    /**
     * Follow-up context command parser (V3.3 Smart Result Control - Part 10).
     * Reuses short-term conversation context for commands like "अब इसे play करो", "play भी करो".
     * Enforces context safety (Chrome task vs YouTube) and 10-minute timeout.
     */
    private fun parseFollowUpCommand(original: String, lower: String): LocalCommandResult? {
        if (!isFollowUpPhrase(lower)) return null

        val rawContext = ConversationContextTracker.currentContext.value

        // Context Safety 1: If user switched tasks (e.g. "Chrome खोलो" -> activeTaskApp = "Chrome")
        if (rawContext != null && rawContext.activeTaskApp != null &&
            !rawContext.activeTaskApp.equals("YouTube", ignoreCase = true) &&
            !rawContext.activeTaskApp.equals("com.google.android.youtube", ignoreCase = true)
        ) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = "आप किस वीडियो की बात कर रहे हैं?"
                ),
                confidence = 1.0f,
                reason = "Task changed to ${rawContext.activeTaskApp}; context discarded for safety"
            )
        }

        // Context Safety 2: Timeout check (> 10 minutes)
        if (rawContext != null && rawContext.isExpired()) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = "आप किस वीडियो की बात कर रहे हैं?"
                ),
                confidence = 1.0f,
                reason = "Follow-up conversation context expired (> 10 minutes)"
            )
        }

        // Context Safety 3: No previous query stored
        val validContext = ConversationContextTracker.getActiveContext()
        if (validContext == null || validContext.query.isNullOrBlank()) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = "आप किस वीडियो की बात कर रहे हैं?"
                ),
                confidence = 1.0f,
                reason = "No active video context found"
            )
        }

        // Valid continuation: Reuse previous video query without Gemini
        val targetQuery = validContext.query
        ConversationContextTracker.updateContext(
            platform = "YOUTUBE",
            query = targetQuery,
            lastAction = "PLAY",
            activeTaskApp = "YouTube"
        )

        return LocalCommandResult(
            recognized = true,
            intent = IntentType.YOUTUBE_SEARCH_AND_PLAY,
            parsedIntent = ParsedIntent(
                type = IntentType.YOUTUBE_SEARCH_AND_PLAY,
                query = targetQuery,
                responseText = "YouTube पर '$targetQuery' खोजा जा रहा है और सबसे उपयुक्त वीडियो चलाया जा रहा है…"
            ),
            parameters = mapOf("query" to targetQuery, "context_used" to "true"),
            confidence = 1.0f,
            reason = "Follow-up command successfully resolved using context: $targetQuery"
        )
    }

    /**
     * Dedicated YouTube Search parser with clean query extraction.
     * Supports:
     * - "YouTube पर song search करो" -> "song"
     * - "YouTube par Free Fire search kro" -> "Free Fire"
     * - "VK Bhuriya का song search करो YouTube पर" -> "VK Bhuriya song"
     * - "Motivational songs search करो YouTube पर" -> "Motivational songs"
     * - "Free Fire का video search करो" -> "Free Fire"
     * - "Search [query] on YouTube" -> "[query]"
     */
    private fun parseYouTubeSearch(original: String, lower: String): LocalCommandResult? {
        val isYtMentioned = lower.contains("youtube") || lower.contains("यूट्यूब") ||
                lower.contains("video") || lower.contains("वीडियो")
        if (!isYtMentioned) return null

        val isSearchCommand = lower.contains("search") || lower.contains("सर्च") ||
                lower.contains("ढूंढो") || lower.contains("खोजो") || lower.contains("ढूँढो") || lower.contains("find")
        if (!isSearchCommand) return null

        // Avoid decomposing multi-step compound commands that were handled in parseMultiStepCommands
        if (lower.contains("खोलो और") || lower.contains("open and") || lower.contains("launch and")) {
            return null
        }

        // Pattern A: Prefix YouTube: "YouTube पर [query] search करो" / "YouTube par [query] search kro"
        val prefixRegex = Regex("""^(?:youtube|यूट्यूब)\s*(?:पर|par|में|me|pe|पे)?\s*(.+?)(?:\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find))?(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val mPrefix = prefixRegex.find(original)
        if (mPrefix != null) {
            val rawQuery = mPrefix.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchResult(cleanedQuery)
            }
        }

        // Pattern B1: Middle YouTube: "[query] YouTube पर search करो" / "[query] youtube par search kro"
        val midYtRegex = Regex("""^(.+?)\s+(?:youtube|यूट्यूब)\s*(?:पर|par|में|me|pe|पे)\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find)(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val mMid = midYtRegex.find(original)
        if (mMid != null) {
            val rawQuery = mMid.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchResult(cleanedQuery)
            }
        }

        // Pattern B2: Suffix YouTube: "[query] search करो YouTube पर" / "[query] search kro youtube par" / "[query] on YouTube"
        val suffixRegex = Regex("""^(.+?)(?:\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find))(?:\s*(?:करो|kro|karo|do|दो))?\s+(?:on\s+youtube|in\s+youtube|youtube\s*पर|youtube\s*par|यूट्यूब\s*पर|youtube\s*me|youtube\s*pe)$""", RegexOption.IGNORE_CASE)
        val mSuffix = suffixRegex.find(original)
        if (mSuffix != null) {
            val rawQuery = mSuffix.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchResult(cleanedQuery)
            }
        }

        // Pattern C: English format: "Search [query] on YouTube" / "Search youtube for [query]"
        val enSearchYt = Regex("""^search\s+(?:youtube\s+for\s+|for\s+)?(.+?)(?:\s+on\s+youtube|\s+in\s+youtube)?$""", RegexOption.IGNORE_CASE)
        val mEn = enSearchYt.find(original)
        if (mEn != null) {
            val rawQuery = mEn.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchResult(cleanedQuery)
            }
        }

        // Pattern D: Video search: "[query] का video search करो" / "[query] video search kro"
        val videoSearchRegex = Regex("""^(.+?)(?:\s*(?:का|ki|ke|के|की))?\s+(?:video|वीडियो)\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find)(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val mVideo = videoSearchRegex.find(original)
        if (mVideo != null) {
            val rawQuery = mVideo.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = true)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createYouTubeSearchResult(cleanedQuery)
            }
        }

        return null
    }

    /**
     * Dedicated Web Search parser with clean query extraction.
     * Supports:
     * - "Google पर Free Fire search करो" -> "Free Fire"
     * - "Google par weather search kro" -> "weather"
     * - "Search Free Fire on Google" -> "Free Fire"
     * - "Free Fire Google पर search करो" -> "Free Fire"
     */
    private fun parseWebSearch(original: String, lower: String): LocalCommandResult? {
        val isGoogleMentioned = lower.contains("google") || lower.contains("गूगल")
        val isSearchCommand = lower.contains("search") || lower.contains("सर्च") ||
                lower.contains("ढूंढो") || lower.contains("खोजो") || lower.contains("ढूँढो") || lower.contains("find")

        // Pattern A: Hindi/Hinglish prefix: "Google पर [query] search करो" / "Google par [query] search kro"
        val prefixRegex = Regex("""^(?:google|गूगल)\s*(?:पर|par|में|me|pe|पे)?\s*(.+?)(?:\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find))?(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val m1 = prefixRegex.find(original)
        if (m1 != null) {
            val rawQuery = m1.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = false)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createSearchAction(cleanedQuery, "Google पर '$cleanedQuery' खोजा जा रहा है…")
            }
        }

        // Pattern B1: Hindi/Hinglish middle: "[query] Google पर search करो" / "[query] google par search kro"
        val midGoogleRegex = Regex("""^(.+?)\s+(?:google|गूगल)\s*(?:पर|par|में|me|pe|पे)\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find)(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val mMid = midGoogleRegex.find(original)
        if (mMid != null) {
            val rawQuery = mMid.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = false)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createSearchAction(cleanedQuery, "Google पर '$cleanedQuery' खोजा जा रहा है…")
            }
        }

        // Pattern B2: Hindi/Hinglish suffix: "[query] search करो Google पर" / "[query] search kro google par"
        val suffixRegex = Regex("""^(.+?)(?:\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find))(?:\s*(?:करो|kro|karo|do|दो))?\s+(?:on\s+google|in\s+google|google\s*पर|google\s*par|गूगल\s*पर|google\s*me|google\s*pe)$""", RegexOption.IGNORE_CASE)
        val m2 = suffixRegex.find(original)
        if (m2 != null) {
            val rawQuery = m2.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = false)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createSearchAction(cleanedQuery, "Google पर '$cleanedQuery' खोजा जा रहा है…")
            }
        }

        // Pattern C: English format: "Search [query] on Google" / "Search google for [query]"
        val enSearch1 = Regex("""^search\s+(?:google\s+for\s+|for\s+)?(.+?)(?:\s+on\s+google|\s+in\s+google)?$""", RegexOption.IGNORE_CASE)
        val m3 = enSearch1.find(original)
        if (m3 != null) {
            val rawQuery = m3.groupValues[1]
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = false)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createSearchAction(cleanedQuery, "Searching Google for '$cleanedQuery'…")
            }
        }

        // Pattern D: "google [query]" (e.g., "google tomorrow weather")
        if (lower.startsWith("google ") && lower.length > 7 && !lower.contains("खोलो") && !lower.contains("open")) {
            val rawQuery = original.substring(7)
            val cleanedQuery = cleanExtractedQuery(rawQuery, isYouTube = false)
            if (cleanedQuery.isNotBlank() && !isIgnoredAppWord(cleanedQuery)) {
                return createSearchAction(cleanedQuery, "Searching Google for '$cleanedQuery'…")
            }
        }

        return null
    }

    /**
     * Strips residual search command affixes ("ka", "का", "ke", "search", "kro", "karo", "par", etc.)
     * to ensure the query is pure and accurate.
     * Example: "VK Bhuriya का song" -> "VK Bhuriya song"
     * Example: "Free Fire search kro" -> "Free Fire"
     */
    private fun cleanExtractedQuery(raw: String, isYouTube: Boolean): String {
        var query = raw.trim()

        // Remove leading/trailing platform markers if any leaked
        query = query.replace(Regex("""(?i)^(?:youtube|यूट्यूब|google|गूगल)\s*(?:पर|par|में|me|pe)?\s*"""), "")
        query = query.replace(Regex("""(?i)\s*(?:on\s+youtube|in\s+youtube|youtube\s*पर|youtube\s*par|यूट्यूब\s*पर|youtube\s*me|youtube\s*pe)$"""), "")
        query = query.replace(Regex("""(?i)\s*(?:on\s+google|in\s+google|google\s*पर|google\s*par|गूगल\s*पर|google\s*me|google\s*pe)$"""), "")

        // Strip trailing search and play compound clauses
        query = query.replace(Regex("""(?i)\s*(?:और|aur|and|फिर|then)\s+(?:relevant\s+video\s+|उपयुक्त\s+वीडियो\s+)?(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao|watch|dekho|देखें|it|the\s*relevant\s*video)(?:\s*(?:करो|kro|karo|do|दो))?$"""), "")
        query = query.replace(Regex("""(?i)\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find)\s+(?:और|aur|and|फिर|then)\s+(?:relevant\s+video\s+|उपयुक्त\s+वीडियो\s+)?(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao|watch|dekho|it)(?:\s*(?:करो|kro|karo|do|दो))?$"""), "")

        // Remove trailing search & play verbs
        query = query.replace(Regex("""(?i)\s+(?:search|सर्च|ढूंढो|खोजो|ढूँढो|find)(?:\s+(?:करो|kro|karo|do|दो))?$"""), "")
        query = query.replace(Regex("""(?i)\s+(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao|watch|dekho)(?:\s+(?:करो|kro|karo|do|दो))?$"""), "")
        query = query.replace(Regex("""(?i)\s+(?:करो|kro|karo|do|दो)$"""), "")

        // Clean internal connective particles like " का song " -> " song ", " ka song " -> " song "
        query = query.replace(Regex("""\s+का\s+(song|गाने|गाना|video|वीडियो)"""), " $1")
        query = query.replace(Regex("""(?i)\s+ka\s+(song|गाने|गाना|video|वीडियो)"""), " $1")
        query = query.replace(Regex("""\s+के\s+(song|गाने|गाना|video|वीडियो)"""), " $1")
        query = query.replace(Regex("""(?i)\s+ke\s+(song|गाने|गाना|video|वीडियो)"""), " $1")

        // Clean trailing "का video" / "video" when not standalone
        if (isYouTube && query.length > 5) {
            query = query.replace(Regex("""(?i)\s*(?:का|ki|ke|के|की)?\s+(?:video|वीडियो)$"""), "")
        }

        // Final trimming
        query = query.trim().replace(Regex("""\s+"""), " ")
        return query
    }

    private fun isIgnoredAppWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT).trim()
        return lower.isEmpty() || lower == "youtube" || lower == "यूट्यूब" ||
                lower == "google" || lower == "गूगल" || lower == "search" || lower == "सर्च" ||
                lower == "settings" || lower == "chrome"
    }

    private fun parseMultiStepCommands(original: String, lower: String): LocalCommandResult? {
        // Pattern 1: "Open YouTube and search (for) [query]" or "Launch YouTube and search [query]"
        val ytPatternEn = Regex("""^(?:open|launch)\s+youtube\s+(?:and|then|\&)\s+(?:search\s*(?:for|google\s*for)?|google)\s+(.+)""", RegexOption.IGNORE_CASE)
        val ytMatchEn = ytPatternEn.find(original)
        if (ytMatchEn != null) {
            val query = cleanExtractedQuery(ytMatchEn.groupValues[1], isYouTube = true)
            return createMultiAction(
                actions = listOf(
                    ParsedIntent(type = IntentType.OPEN_APP, app = "YouTube", responseText = "Opening YouTube"),
                    ParsedIntent(type = IntentType.YOUTUBE_SEARCH, query = query, responseText = "Searching YouTube for $query")
                ),
                responseText = "Opening YouTube and searching for '$query'.",
                reason = "Multi-step local decomposition: YouTube + Search"
            )
        }

        // Pattern 1b: "Open YouTube and play [query]"
        val ytPlayPatternEn = Regex("""^(?:open|launch)\s+youtube\s+(?:and|then|\&)\s+(?:play|watch)\s+(.+)""", RegexOption.IGNORE_CASE)
        val ytPlayMatchEn = ytPlayPatternEn.find(original)
        if (ytPlayMatchEn != null) {
            val query = cleanExtractedQuery(ytPlayMatchEn.groupValues[1], isYouTube = true)
            return createYouTubeSearchAndPlayResult(query)
        }

        // Pattern 2: "YouTube खोलो और [query] search करो" / "यूट्यूब खोलो और [query] सर्च करो"
        val ytPatternHi = Regex("""^(?:youtube|यूट्यूब)\s*(?:खोलो|ओपन करो|open karo|open kro)\s*(?:और|फिर|and|then)\s*(?:youtube\s*पर|google\s*पर)?\s*(.+?)\s*(?:search|सर्च|ढूंढो|खोजो)\s*(?:करो|kro|karo)?$""", RegexOption.IGNORE_CASE)
        val ytMatchHi = ytPatternHi.find(original)
        if (ytMatchHi != null) {
            val query = cleanExtractedQuery(ytMatchHi.groupValues[1], isYouTube = true)
            return createMultiAction(
                actions = listOf(
                    ParsedIntent(type = IntentType.OPEN_APP, app = "YouTube", responseText = "YouTube खोला जा रहा है"),
                    ParsedIntent(type = IntentType.YOUTUBE_SEARCH, query = query, responseText = "YouTube पर '$query' सर्च किया जा रहा है")
                ),
                responseText = "YouTube खोला जा रहा है और '$query' सर्च किया जा रहा है।",
                reason = "Multi-step local decomposition: YouTube + Search (Hindi)"
            )
        }

        // Pattern 2b: "YouTube खोलो और [query] चलाओ / play करो"
        val ytPlayPatternHi = Regex("""^(?:youtube|यूट्यूब)\s*(?:खोलो|ओपन करो|open karo|open kro)\s*(?:और|फिर|and|then)\s*(?:youtube\s*पर|google\s*पर)?\s*(.+?)\s*(?:play|चलाओ|चला\s*दो|chalao|बजाओ|bajao)(?:\s*(?:करो|kro|karo|do|दो))?$""", RegexOption.IGNORE_CASE)
        val ytPlayMatchHi = ytPlayPatternHi.find(original)
        if (ytPlayMatchHi != null) {
            val query = cleanExtractedQuery(ytPlayMatchHi.groupValues[1], isYouTube = true)
            return createYouTubeSearchAndPlayResult(query)
        }

        // Pattern 3: "Open Chrome and search (for) [query]"
        val chromePatternEn = Regex("""^(?:open|launch)\s+chrome\s+(?:and|then|\&)\s+(?:search\s*(?:for|google\s*for)?|google)\s+(.+)""", RegexOption.IGNORE_CASE)
        val chromeMatchEn = chromePatternEn.find(original)
        if (chromeMatchEn != null) {
            val query = cleanExtractedQuery(chromeMatchEn.groupValues[1], isYouTube = false)
            return createMultiAction(
                actions = listOf(
                    ParsedIntent(type = IntentType.OPEN_APP, app = "Chrome", responseText = "Opening Chrome"),
                    ParsedIntent(type = IntentType.WEB_SEARCH, query = query, responseText = "Searching for $query")
                ),
                responseText = "Opening Chrome and searching for '$query'.",
                reason = "Multi-step local decomposition: Chrome + Web Search"
            )
        }

        // Pattern 4: "Chrome खोलो और [query] search करो"
        val chromePatternHi = Regex("""^(?:chrome|क्रोम)\s*(?:खोलो|kholo|ओपन करो|open karo|open kro)\s*(?:और|aur|फिर|and|then)\s*(?:google\s*पर|गूगल\s*पर)?\s*(.+?)\s*(?:search|सर्च|ढूंढो|खोजो)\s*(?:करो|kro|karo)?$""", RegexOption.IGNORE_CASE)
        val chromeMatchHi = chromePatternHi.find(original)
        if (chromeMatchHi != null) {
            val query = cleanExtractedQuery(chromeMatchHi.groupValues[1], isYouTube = false)
            return createMultiAction(
                actions = listOf(
                    ParsedIntent(type = IntentType.OPEN_APP, app = "Chrome", responseText = "Chrome खोला जा रहा है"),
                    ParsedIntent(type = IntentType.WEB_SEARCH, query = query, responseText = "'$query' सर्च किया जा रहा है")
                ),
                responseText = "Chrome खोला जा रहा है और '$query' सर्च किया जा रहा है।",
                reason = "Multi-step local decomposition: Chrome + Search (Hindi)"
            )
        }

        // Pattern 5: "Open Chrome and then open Google" / "Chrome खोलो और Google खोलो"
        if ((lower.contains("open chrome") || lower.contains("chrome खोलो") || lower.contains("chrome open")) &&
            (lower.contains("open google") || lower.contains("google खोलो") || lower.contains("गूगल खोलो") || lower.contains("google open"))) {
            return createMultiAction(
                actions = listOf(
                    ParsedIntent(type = IntentType.OPEN_APP, app = "Chrome"),
                    ParsedIntent(type = IntentType.OPEN_URL, target = "https://www.google.com")
                ),
                responseText = "Opening Chrome and navigating to Google.",
                reason = "Multi-step local decomposition: Chrome + Google URL"
            )
        }

        // Pattern 6: "Open Settings and go back" / "Settings खोलो और back जाओ"
        if ((lower.contains("settings") || lower.contains("सेटिंग")) &&
            (lower.contains("back") || lower.contains("पीछे जाओ") || lower.contains("वापस जाओ"))) {
            return createMultiAction(
                actions = listOf(
                    ParsedIntent(type = IntentType.OPEN_SETTINGS),
                    ParsedIntent(type = IntentType.BACK)
                ),
                responseText = "Opening Settings and returning back.",
                reason = "Multi-step local decomposition: Settings + Back"
            )
        }

        // Pattern 7: Generic multi-action command splitting by conjunctions:
        // " और ", " aur ", " and then ", " and ", " then ", " फिर ", " fir ", " ke baad ", " के बाद ", " & "
        val splitRegex = Regex("""\s+(?:और|aur|and\s+then|and|then|फिर|fir|ke\s+baad|के\s+बाद|\&)\s+""", RegexOption.IGNORE_CASE)
        val segments = original.split(splitRegex).map { it.trim() }.filter { it.isNotBlank() }
        if (segments.size >= 2) {
            val subIntents = mutableListOf<ParsedIntent>()
            var allRecognized = true
            for (segment in segments) {
                val subResult = parseSingleSegment(segment)
                if (subResult != null && subResult.recognized && subResult.parsedIntent != null) {
                    if (subResult.parsedIntent.type == IntentType.MULTI_ACTION) {
                        subIntents.addAll(subResult.parsedIntent.actions)
                    } else {
                        subIntents.add(subResult.parsedIntent)
                    }
                } else {
                    allRecognized = false
                    break
                }
            }
            if (allRecognized && subIntents.size >= 2) {
                val responseSummary = subIntents.mapNotNull { it.responseText }.joinToString(" और ")
                return createMultiAction(
                    actions = subIntents,
                    responseText = if (responseSummary.isNotBlank()) responseSummary else "क्रमशः क्रियाएँ निष्पादित की जा रही हैं।",
                    reason = "Generic multi-step local decomposition (${subIntents.size} actions)"
                )
            }
        }

        return null
    }

    /**
     * Parses a single independent command segment without multi-step recursion.
     */
    fun parseSingleSegment(segment: String): LocalCommandResult? {
        val norm = normalizeInput(segment)
        if (norm.isBlank()) return null
        val cleaned = cleanConversationalFillers(norm)
        val lower = cleaned.lowercase(Locale.getDefault())

        return parseClearChat(lower)
            ?: parsePreferences(cleaned, lower)
            ?: parseContextQuery(cleaned, lower)
            ?: parseWeatherQuery(cleaned, lower)
            ?: parseWebsiteIntent(cleaned, lower)
            ?: parseYouTubeSearchAndPlay(cleaned, lower)
            ?: parseFollowUpCommand(cleaned, lower)
            ?: parseYouTubeSearch(cleaned, lower)
            ?: parseWebSearch(cleaned, lower)
            ?: parseNavigation(lower)
            ?: parseUrl(cleaned, lower)
            ?: parseAppLaunch(cleaned, lower)
            ?: parseGreetings(lower)
    }

    private fun parseUrl(original: String, lower: String): LocalCommandResult? {
        var targetUrl: String? = null
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
            targetUrl = original
        } else if (lower.startsWith("open http://") || lower.startsWith("open https://") || lower.startsWith("open www.")) {
            targetUrl = original.removePrefix("open ").removePrefix("Open ").trim()
        }

        if (targetUrl != null) {
            val formatted = if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) "https://$targetUrl" else targetUrl
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.OPEN_URL,
                parsedIntent = ParsedIntent(
                    type = IntentType.OPEN_URL,
                    target = formatted,
                    responseText = "Opening $formatted"
                ),
                parameters = mapOf("target" to formatted),
                confidence = 1.0f,
                reason = "Direct URL match"
            )
        }
        return null
    }

    private fun parseContextAppLaunch(original: String, lower: String): LocalCommandResult? {
        val pronounAppLaunchKeywords = setOf(
            "isko kholo", "ise kholo", "use kholo", "isko open karo", "ise open karo", "use open karo",
            "isko open kro", "ise open kro", "use open kro", "open it", "launch it", "start it",
            "isko chalao", "ise chalao", "use chalao", "ise chalu karo", "isko chalu karo",
            "is app ko kholo", "us app ko kholo", "is app ko open karo", "us app ko open karo",
            "इसे खोलो", "इसको खोलो", "उसे खोलो", "इसे ओपन करो", "इसको ओपन करो", "उसे ओपन करो",
            "इसे चलाओ", "इसको चलाओ", "उसे चलाओ", "इसे चालू करो", "इसको चालू करो", "ओपन करो इसे"
        )
        val isPronounAppMatch = pronounAppLaunchKeywords.contains(lower) ||
                lower.matches(Regex("""^(?:isko|ise|use|is\s+app\s+ko|us\s+app\s+ko|इसे|इसको|उसे)\s*(?:ko\s+)?(?:kholo|open\s*karo|open\s*kro|launch\s*karo|launch\s*kro|chalao|chalu\s*karo|खोलो|ओपन\s*करो|लॉन्च\s*करो|चलाओ|चालू\s*करो)$""", RegexOption.IGNORE_CASE))

        if (!isPronounAppMatch) return null

        val context = ConversationContextTracker.getActiveContext()
        if (context == null) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = "आप कौन सा app खोलना चाहते हैं?"
                ),
                confidence = 1.0f,
                reason = "No active context for pronoun app launch"
            )
        }

        if (context.isAmbiguousEntity) {
            val candidatesStr = context.candidateEntities.joinToString(" या ")
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = "आप $candidatesStr में से कौन सा app खोलना चाहते हैं?"
                ),
                confidence = 1.0f,
                reason = "Ambiguous context for pronoun app launch"
            )
        }

        val targetApp = context.effectiveEntity
        if (targetApp.isNullOrBlank()) {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.GENERAL_CHAT,
                parsedIntent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    responseText = "आप कौन सा app खोलना चाहते हैं?"
                ),
                confidence = 1.0f,
                reason = "Empty effective entity in context"
            )
        }

        return createAppLaunchResult(targetApp, "$targetApp खोला जा रहा है…")
    }

    private fun parseAppLaunch(original: String, lower: String): LocalCommandResult? {
        // 0. Pronoun & Context App Launch ("isko kholo", "ise kholo", "open it")
        val contextAppResult = parseContextAppLaunch(original, lower)
        if (contextAppResult != null) return contextAppResult

        // 1. YouTube
        if (lower == "youtube" || lower == "open youtube" || lower == "launch youtube" ||
            lower == "youtube खोलो" || lower == "यूट्यूब खोलो" || lower == "youtube open karo" ||
            lower == "youtube open kro" || lower == "youtube kholo" || lower == "youtube चलाओ") {
            return createAppLaunchResult("YouTube", "YouTube खोला जा रहा है…")
        }

        // 2. Chrome
        if (lower == "chrome" || lower == "open chrome" || lower == "launch chrome" ||
            lower == "chrome खोलो" || lower == "क्रोम खोलो" || lower == "chrome open karo" ||
            lower == "chrome open kro" || lower == "chrome kholo" || lower == "chrome चलाओ" ||
            lower == "chrome launch kro" || lower == "chrome launch karo") {
            return createAppLaunchResult("Chrome", "Google Chrome खोला जा रहा है…")
        }

        // 3. Settings
        if (lower == "settings" || lower == "open settings" || lower == "open setting" ||
            lower == "settings खोलो" || lower == "setting खोलो" || lower == "सेटिंग्स खोलो" ||
            lower == "सेटिंग खोलो" || lower == "settings open karo" || lower == "settings kholo") {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.OPEN_SETTINGS,
                parsedIntent = ParsedIntent(
                    type = IntentType.OPEN_SETTINGS,
                    responseText = "सेटिंग्स खोली जा रही हैं…"
                ),
                confidence = 1.0f,
                reason = "Direct Settings match"
            )
        }

        // 4. Google Homepage
        if (lower == "google" || lower == "open google" || lower == "google खोलो" ||
            lower == "गूगल खोलो" || lower == "google open karo" || lower == "google kholo") {
            return LocalCommandResult(
                recognized = true,
                intent = IntentType.OPEN_URL,
                parsedIntent = ParsedIntent(
                    type = IntentType.OPEN_URL,
                    target = "https://www.google.com",
                    responseText = "Google खोला जा रहा है…"
                ),
                confidence = 1.0f,
                reason = "Google homepage match"
            )
        }

        // 5. WhatsApp
        if (lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("वाट्सएप")) {
            if (lower.contains("खोलो") || lower.contains("open") || lower.contains("kholo") || lower.contains("launch") || lower.contains("चलाओ") || lower == "whatsapp") {
                return createAppLaunchResult("WhatsApp", "WhatsApp खोला जा रहा है…")
            }
        }

        // 6. Maps
        if (lower.contains("maps") || lower.contains("मैप्स") || lower.contains("गूगल मैप्स") || lower.contains("नक्शा")) {
            if (lower.contains("खोलो") || lower.contains("open") || lower.contains("kholo") || lower.contains("launch") || lower == "maps") {
                return createAppLaunchResult("Maps", "Google Maps खोला जा रहा है…")
            }
        }

        // 7. Known Standard Apps Dictionary
        val knownApps = mapOf(
            "camera" to "Camera", "कैमरा" to "Camera",
            "gallery" to "Gallery", "गैलरी" to "Gallery", "photos" to "Photos",
            "calculator" to "Calculator", "कैलकुलेटर" to "Calculator", "calc" to "Calculator",
            "clock" to "Clock", "अलार्म" to "Clock", "घड़ी" to "Clock",
            "phone" to "Phone", "dialer" to "Phone", "डायलर" to "Phone", "फोन" to "Phone",
            "contacts" to "Contacts", "कांटेक्ट" to "Contacts", "कॉन्टैक्ट्स" to "Contacts",
            "messages" to "Messages", "मैसेज" to "Messages", "sms" to "Messages",
            "gmail" to "Gmail", "ईमेल" to "Gmail", "email" to "Gmail",
            "play store" to "Play Store", "प्ले स्टोर" to "Play Store", "playstore" to "Play Store",
            "instagram" to "Instagram", "इंस्टाग्राम" to "Instagram", "insta" to "Instagram",
            "telegram" to "Telegram", "टेलीग्राम" to "Telegram",
            "spotify" to "Spotify", "स्पॉटिफाई" to "Spotify",
            "free fire" to "Free Fire", "freefire" to "Free Fire", "फ्री फायर" to "Free Fire"
        )

        for ((trigger, canonicalName) in knownApps) {
            if (lower == trigger || lower == "open $trigger" || lower == "launch $trigger" || lower == "$trigger खोलो" ||
                lower == "$trigger kholo" || lower == "$trigger open karo" || lower == "$trigger open kro" ||
                lower == "$trigger launch karo" || lower == "$trigger launch kro" || lower == "$trigger चलाओ" ||
                lower == "$trigger khol do" || lower == "$trigger chalu karo") {
                return createAppLaunchResult(canonicalName, "$canonicalName खोला जा रहा है…")
            }
        }

        // 8. Generic Hindi / Hinglish Suffix: "[app] खोलो" / "[app] kholo" / "[app] open karo" / "[app] launch kro" / "[app] चलाओ"
        val genericHindi = Regex("""^(.+?)\s*(?:खोलो|खोल\s*दो|खोल\s*दीजिए|खोल\s*दे|ओपन\s*करो|ओपन\s*कर\s*दो|ओपन\s*कर|लॉन्च\s*करो|लॉन्च\s*कर\s*दो|लॉन्च\s*कर|स्टार्ट\s*करो|चलाओ|चला\s*दो|चालू\s*करो|चालू\s*कर\s*दो|kholo|khol\s*do|khol\s*de|kholiye|open\s*karo|open\s*kro|open\s*kar\s*do|open\s*kr\s*do|launch\s*karo|launch\s*kro|launch\s*kar\s*do|launch\s*kr\s*do|start\s*karo|start\s*kro|chalao|chala\s*do|chalu\s*karo|chalu\s*kro)$""", RegexOption.IGNORE_CASE)
        val ghMatch = genericHindi.find(original)
        if (ghMatch != null) {
            var appName = ghMatch.groupValues[1].trim()
            appName = appName.replace(Regex("""^(?:the\s+|app\s+|mera\s+|my\s+)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""(?i)\s+(?:app|ऐप|वाला\s*ऐप|वाला\s*app)$"""), "").trim()
            if (appName.isNotBlank() && !appName.equals("chat", true) && !appName.equals("चैट", true) && !appName.equals("conversation", true)) {
                return createAppLaunchResult(appName, "$appName खोला जा रहा है…")
            }
        }

        // 9. Generic English Prefix: "open [app]" / "launch [app]" / "start [app]"
        val genericEnglish = Regex("""^(?:open|launch|start|run|ओपन|लॉन्च|स्टार्ट)\s+(?:the\s+)?(?:app\s+)?(.+)$""", RegexOption.IGNORE_CASE)
        val geMatch = genericEnglish.find(original)
        if (geMatch != null) {
            var appName = geMatch.groupValues[1].trim()
            appName = appName.replace(Regex("""(?i)\s+(?:app|ऐप)$"""), "").trim()
            if (appName.isNotBlank() && !appName.equals("chat", true) && !appName.startsWith("http://") && !appName.startsWith("https://")) {
                return createAppLaunchResult(appName, "Opening $appName…")
            }
        }

        return null
    }

    private fun createAppLaunchResult(appName: String, responseText: String): LocalCommandResult {
        ConversationContextTracker.updateContext(
            platform = null,
            query = null,
            lastAction = "OPEN_APP",
            activeTaskApp = appName
        )
        return LocalCommandResult(
            recognized = true,
            intent = IntentType.OPEN_APP,
            parsedIntent = ParsedIntent(
                type = IntentType.OPEN_APP,
                app = appName,
                responseText = responseText
            ),
            parameters = mapOf("app" to appName),
            confidence = 1.0f,
            reason = "Local app launch match: $appName"
        )
    }

    private fun createYouTubeSearchResult(query: String): LocalCommandResult {
        ConversationContextTracker.updateContext(
            platform = "YOUTUBE",
            query = query,
            lastAction = "SEARCH",
            activeTaskApp = "YouTube"
        )
        return LocalCommandResult(
            recognized = true,
            intent = IntentType.YOUTUBE_SEARCH,
            parsedIntent = ParsedIntent(
                type = IntentType.YOUTUBE_SEARCH,
                query = query,
                responseText = "YouTube पर '$query' खोजा जा रहा है…"
            ),
            parameters = mapOf("query" to query),
            confidence = 1.0f,
            reason = "Local YouTube search match: $query"
        )
    }

    private fun createYouTubeSearchAndPlayResult(query: String): LocalCommandResult {
        ConversationContextTracker.updateContext(
            platform = "YOUTUBE",
            query = query,
            lastAction = "PLAY",
            activeTaskApp = "YouTube"
        )
        return LocalCommandResult(
            recognized = true,
            intent = IntentType.YOUTUBE_SEARCH_AND_PLAY,
            parsedIntent = ParsedIntent(
                type = IntentType.YOUTUBE_SEARCH_AND_PLAY,
                query = query,
                responseText = "YouTube पर '$query' खोजा जा रहा है और सबसे उपयुक्त वीडियो चलाया जा रहा है…"
            ),
            parameters = mapOf("query" to query, "playRelevant" to true),
            confidence = 1.0f,
            reason = "Local YouTube search & play match: $query"
        )
    }

    private fun createSearchAction(query: String, responseText: String): LocalCommandResult {
        ConversationContextTracker.updateContext(
            platform = "GOOGLE",
            query = query,
            lastAction = "SEARCH",
            activeTaskApp = "Google"
        )
        return LocalCommandResult(
            recognized = true,
            intent = IntentType.WEB_SEARCH,
            parsedIntent = ParsedIntent(
                type = IntentType.WEB_SEARCH,
                query = query,
                responseText = responseText
            ),
            parameters = mapOf("query" to query),
            confidence = 1.0f,
            reason = "Local web search match: $query"
        )
    }

    private fun createMultiAction(actions: List<ParsedIntent>, responseText: String, reason: String): LocalCommandResult {
        return LocalCommandResult(
            recognized = true,
            intent = IntentType.MULTI_ACTION,
            parsedIntent = ParsedIntent(
                type = IntentType.MULTI_ACTION,
                actions = actions,
                responseText = responseText
            ),
            parameters = mapOf("actionsCount" to actions.size),
            confidence = 1.0f,
            reason = reason
        )
    }
}
