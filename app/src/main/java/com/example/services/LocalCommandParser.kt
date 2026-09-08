package com.example.services

import com.example.models.ActionResult
import com.example.models.IntentType
import com.example.models.LocalCommandResult
import com.example.models.MessageCategory
import com.example.models.ParsedIntent
import com.example.platform.android.AppLauncher
import java.util.Locale

class LocalCommandParser {

    fun parse(rawInput: String): LocalCommandResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return LocalCommandResult(handled = false, reason = "Empty input")
        }

        val normalized = normalizeText(trimmed)

        // 1. User Feedback & Failure Reporting ("nahi hua", "ye nahi hua", "fail hua", etc.)
        // Must be checked FIRST before any action or search intent!
        val failureFeedbackResult = checkFailureAndCorrection(normalized, trimmed)
        if (failureFeedbackResult != null) {
            return failureFeedbackResult
        }

        // 2. Context Questions & Reference Queries ("1 wale me tumne kya kiya", "mene kya bola", etc.)
        val contextResult = checkContextAndConversationQuestions(normalized)
        if (contextResult != null) {
            return contextResult
        }

        // 3. Meta Instructions ("me chahta hu vo sare steps youtube par ho or vo tum khud kro")
        // Must be evaluated before YouTube search to avoid converting instructions into search queries!
        val metaResult = checkMetaInstruction(normalized, trimmed)
        if (metaResult != null) {
            return metaResult
        }

        // 4. Memory Queries ("tumhe kya yaad hai", "meri preferences kya hain")
        val memoryResult = checkMemoryQuery(normalized)
        if (memoryResult != null) {
            return memoryResult
        }

        // 5. Context-dependent Confirmations & Denials ("haan", "nahi")
        val confirmDenyResult = checkConfirmationAndDenial(normalized)
        if (confirmDenyResult != null) {
            return confirmDenyResult
        }

        // 6. Greetings, General Conversation & Identity Questions ("hii", "kaise ho", "who are you")
        val greetingOrConvResult = checkGreetingAndConversation(normalized)
        if (greetingOrConvResult != null) {
            return greetingOrConvResult
        }

        // 7. Challenge / Autonomous Test Requests ("tumhari marji se koi random target pura karo jisme kam se kam 6 steps hona chahiye")
        val challengeResult = checkChallengeRequest(normalized)
        if (challengeResult != null) {
            return challengeResult
        }

        // 8. Multi-command check (e.g. "... aur ...")
        val multiResult = checkMultiCommand(trimmed)
        if (multiResult != null) {
            return multiResult
        }

        // 9. Cancel request
        if (matchesAny(normalized, listOf("cancel", "cancel karo", "rok do", "rehne do", "radd karo", "रद्द करो", "रहने दो", "रोक दो", "stop action", "stop", "band karo", "ruk jao", "रुक जाओ"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.CANCEL_REQUEST, category = MessageCategory.CANCEL_REQUEST),
                responseText = "कमांड रद्द कर दी गई है।",
                category = MessageCategory.CANCEL_REQUEST
            )
        }

        // 10. Clear chat
        if (matchesAny(normalized, listOf("clear chat", "chat clear karo", "chat saaf karo", "delete chat", "clear history", "saaf karo", "चैट साफ़ करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.CLEAR_CHAT, category = MessageCategory.NEW_COMMAND),
                actionResult = ActionResult(success = true, message = "बातचीत साफ़ कर दी गई है (Chat cleared)"),
                responseText = "बातचीत साफ़ कर दी गई है। अब आप नया सवाल पूछ सकते हैं।",
                category = MessageCategory.NEW_COMMAND
            )
        }

        // 9. Navigation: Back / Home / Recents
        if (matchesAny(normalized, listOf("go back", "back jao", "back", "peeche jao", "wapas jao", "wapas", "पीछे जाओ", "वापस"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GO_BACK, category = MessageCategory.NEW_COMMAND),
                responseText = "वापस जाया जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        if (matchesAny(normalized, listOf("go home", "home screen", "home jao", "ghar jao", "home", "होम स्क्रीन", "होम जाओ", "होम"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GO_HOME, category = MessageCategory.NEW_COMMAND),
                responseText = "होम स्क्रीन पर जाया जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        // 10. Scroll navigation
        if (matchesAny(normalized, listOf("scroll down", "neeche karo", "neeche scroll karo", "neeche jao", "नीचे करो", "नीचे स्क्रॉल करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.SCROLL_DOWN, category = MessageCategory.NEW_COMMAND),
                responseText = "नीचे स्क्रॉल किया जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        if (matchesAny(normalized, listOf("scroll up", "upar karo", "upar scroll karo", "upar jao", "ऊपर करो", "ऊपर स्क्रॉल करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.SCROLL_UP, category = MessageCategory.NEW_COMMAND),
                responseText = "ऊपर स्क्रॉल किया जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        // 11. Read screen
        if (matchesAny(normalized, listOf("read screen", "screen padho", "screen par kya hai", "kya dikh raha hai", "स्क्रीन पढ़ो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.READ_SCREEN, category = MessageCategory.NEW_COMMAND),
                responseText = "स्क्रीन की जानकारी पढ़ी जा रही है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        // 12. Media controls: Play / Pause / Stop
        val isPauseCommand = listOf("pause video", "pause song", "pause", "video roko", "gaana roko", "ruk jao", "पॉज़ करो", "रोको")
            .any { normalized == it || normalized.startsWith("$it ") }
        if (isPauseCommand) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.PAUSE, target = "pause", category = MessageCategory.NEW_COMMAND),
                responseText = "पॉज़ किया जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        val isDirectPlayCommand = (normalized == "play" || normalized == "chalao" || normalized == "चलाओ" ||
                normalized == "resume" || normalized == "play video" || normalized == "video chalao")
        if (isDirectPlayCommand) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.PLAY, target = "play", category = MessageCategory.NEW_COMMAND),
                responseText = "प्ले किया जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        // 13. Camera
        if (normalized.contains("camera") || normalized.contains("कैमरा") || normalized.contains("photo khincho") || normalized.contains("फोटो खींचो")) {
            if (hasLaunchSemantics(normalized) || normalized == "camera" || normalized == "कैमरा") {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = "camera", category = MessageCategory.NEW_COMMAND),
                    responseText = "कैमरा खोला जा रहा है...",
                    category = MessageCategory.NEW_COMMAND
                )
            }
        }

        // 14. Dialer
        if (normalized.contains("dialer") || normalized.contains("phone dialer") || normalized.contains("डायलर") || normalized.contains("call lagao") || normalized.contains("phone milao")) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_APP, app = "dialer", category = MessageCategory.NEW_COMMAND),
                responseText = "फोन डायलर खोला जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        // 15. Settings
        if (normalized.contains("settings") || normalized.contains("सेटिंग्स") || normalized.contains("setting")) {
            if (hasLaunchSemantics(normalized) || normalized == "settings" || normalized == "setting") {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_SETTINGS, category = MessageCategory.NEW_COMMAND),
                    responseText = "सिस्टम सेटिंग्स खोली जा रही हैं...",
                    category = MessageCategory.NEW_COMMAND
                )
            }
        }

        // 16. YouTube Search & Play (With robust query extraction)
        val ytResult = parseYouTubeCommand(trimmed, normalized)
        if (ytResult != null) {
            return ytResult
        }

        // 17. Chrome & Specific Web Page opening
        val chromeResult = parseChromeCommand(trimmed, normalized)
        if (chromeResult != null) {
            return chromeResult
        }

        // 18. Check App Launch Intent for installed apps
        val appLaunchResult = checkAppLaunch(normalized, trimmed)
        if (appLaunchResult != null) {
            return appLaunchResult
        }

        // 19. Direct URL
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || (normalized.startsWith("www.") && normalized.contains("."))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_URL, target = trimmed, category = MessageCategory.NEW_COMMAND),
                responseText = "वेबसाइट खोली जा रही है: $trimmed",
                category = MessageCategory.NEW_COMMAND
            )
        }

        // 20. Web Search
        if (normalized.startsWith("search ") || normalized.startsWith("google ") || normalized.contains("search karo") || normalized.contains("सर्च करो") || normalized.contains("dhoondho") || normalized.contains("khojo")) {
            val query = extractSearchQuery(trimmed)
            if (query.isNotEmpty()) {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.WEB_SEARCH, query = query, category = MessageCategory.NEW_COMMAND),
                    responseText = "Google पर '$query' खोजा जा रहा है...",
                    category = MessageCategory.NEW_COMMAND
                )
            }
        }

        return LocalCommandResult(handled = false, reason = "No local pattern matched")
    }

    /**
     * Detects user feedback about failure or incorrect interpretation of the previous request.
     * Must NEVER be parsed as a YouTube or web search!
     */
    private fun checkFailureAndCorrection(normalized: String, original: String): LocalCommandResult? {
        val correctionPhrases = listOf(
            "nahi mera matlab", "mera matlab ye tha", "mera matlab kuch aur tha", "mera matlab ye nahi tha",
            "mera matlab", "maine search karne ko nahi kaha tha", "maine search karne ko nahi bola tha",
            "maine search nahi bola tha", "maine search karne ko nahi bola",
            "ye nahi kaha tha", "ye nahi bola tha", "maine yeh nahi kah raha tha",
            "main yeh nahi kah raha tha", "galat hai", "ye galat hai", "wrong hai",
            "tum galat samjhe", "tumne galat samjha", "aisa nahi", "ऐसा नहीं",
            "galat interpret kiya", "maine aisa nahi bola tha"
        )
        if (correctionPhrases.any { normalized.contains(it) }) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.CORRECT_PREVIOUS_RESULT,
                    userCorrection = original,
                    confidence = 1.0f,
                    category = MessageCategory.CORRECTION
                ),
                responseText = "माफ़ कीजिए, मेरी समझने में गलती हुई। मैंने इसे ठीक कर लिया है और अपनी समझ को अपडेट कर लिया है।",
                category = MessageCategory.CORRECTION,
                confidence = 1.0f
            )
        }

        val failurePhrases = listOf(
            "nahi hua", "kuch nahi hua", "ye nahi hua", "fail hua", "fail ho gaya",
            "kaam nahi hua", "nahi chala", "video nahi chala", "chala nahi",
            "kuch bhi nahi hua", "not working", "didn't work", "failed",
            "नहीं हुआ", "कुछ नहीं हुआ", "फेल हो गया", "फेल हुआ", "काम नहीं हुआ", "नहीं चला",
            "tumne galat kiya"
        )
        val isWhyQuestion = normalized.contains("kyu") || normalized.contains("kyun") ||
                normalized.contains("why") || normalized.contains("क्यों")
        if (!isWhyQuestion && failurePhrases.any { normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") }) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.REPORT_FAILURE,
                    userCorrection = "nahi hua",
                    confidence = 1.0f,
                    category = MessageCategory.FAILURE_FEEDBACK
                ),
                responseText = "ठीक है, पिछली request सफल नहीं हुई। मैं चाहें तो उसे दोबारा अलग तरीके से try कर सकती हूँ।",
                category = MessageCategory.FAILURE_FEEDBACK,
                confidence = 1.0f
            )
        }

        return null
    }

    private fun checkMemoryQuery(normalized: String): LocalCommandResult? {
        val memoryPhrases = listOf(
            "tumhe kya yaad hai", "meri preferences kya hain", "tumne kya yaad rakha hai",
            "tum mujhe kya yaad rakhte ho", "what do you remember", "kya yaad hai tumhe",
            "meri memory", "yaad kya hai", "tumhe kya pata hai mere bare mein"
        )
        if (memoryPhrases.any { normalized.contains(it) || it.contains(normalized) }) {
            val reply = "मुझे आपकी प्राथमिकताओं और सीखे गए स्किल्स की जानकारी याद है। कोई भी संवेदनशील डेटा केवल आपके डिवाइस पर सुरक्षित रहता है।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.MEMORY_QUERY,
                    responseText = reply,
                    category = MessageCategory.MEMORY_QUERY,
                    confidence = 1.0f
                ),
                responseText = reply,
                category = MessageCategory.MEMORY_QUERY,
                confidence = 1.0f
            )
        }
        return null
    }

    private fun checkConfirmationAndDenial(normalized: String): LocalCommandResult? {
        val confirmPhrases = listOf("haan", "हाँ", "yes", "ha", "haa", "haa ji", "yes please", "sure", "bilkul")
        if (confirmPhrases.any { normalized == it }) {
            val reply = "जी ठीक है, बताइए क्या करना है।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.CONFIRMATION,
                    responseText = reply,
                    category = MessageCategory.CONFIRMATION,
                    confidence = 1.0f
                ),
                responseText = reply,
                category = MessageCategory.CONFIRMATION,
                confidence = 1.0f
            )
        }

        val denyPhrases = listOf("nahi", "नहीं", "no", "nah", "nope", "nahi rehne do", "rehne do mat karo")
        if (denyPhrases.any { normalized == it }) {
            val reply = "जी ठीक है।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.DENIAL,
                    responseText = reply,
                    category = MessageCategory.DENIAL,
                    confidence = 1.0f
                ),
                responseText = reply,
                category = MessageCategory.DENIAL,
                confidence = 1.0f
            )
        }
        return null
    }

    private fun checkGreetingAndConversation(normalized: String): LocalCommandResult? {
        val greetingPhrases = listOf(
            "hi", "hii", "hiii", "hello", "hey", "heyy", "namaste", "namaskar",
            "नमस्ते", "हेलो", "सुप्रभात", "pranam", "प्रणाम"
        )
        if (greetingPhrases.any { normalized == it }) {
            val reply = "नमस्ते! मैं यहाँ हूँ। बताइए, क्या करना है?"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.GREETING,
                    responseText = reply,
                    category = MessageCategory.GREETING,
                    confidence = 1.0f
                ),
                responseText = reply,
                category = MessageCategory.GREETING,
                confidence = 1.0f
            )
        }

        val conversationPhrases = listOf(
            "kaise ho", "how are you", "kya haal hai", "कैसी हो", "कैसे हो",
            "tum kaise ho", "आप कैसे हो", "theek hai", "thik hai", "achha", "accha",
            "samajh gaya", "samajh gayi", "samajh aa gaya", "ok", "okay", "wah",
            "badhiya", "shabash", "thanks", "thank you", "shukriya", "dhanyawad", "धन्यवाद"
        )
        if (conversationPhrases.any { normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") }) {
            val reply = when {
                normalized.contains("kaise ho") || normalized.contains("हाल") ->
                    "मैं बिल्कुल ठीक हूँ! बताइए, आज क्या करना है?"
                normalized.contains("thanks") || normalized.contains("shukriya") || normalized.contains("धन्यवाद") ->
                    "आपका स्वागत है! कोई और काम हो तो बताइए।"
                else ->
                    "जी, बताइए आगे क्या करना है।"
            }
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.GENERAL_CONVERSATION,
                    query = normalized,
                    responseText = reply,
                    category = MessageCategory.GENERAL_CONVERSATION,
                    confidence = 1.0f
                ),
                responseText = reply,
                category = MessageCategory.GENERAL_CONVERSATION,
                confidence = 1.0f
            )
        }

        val identityQuestions = listOf(
            "myra kaun ho", "tum kaun ho", "who are you", "tum kya kar sakti ho",
            "what can you do", "aap kaun ho", "myra kya hai", "tum kon ho"
        )
        if (identityQuestions.any { normalized.contains(it) }) {
            val reply = "मैं Myra हूँ, आपकी स्मार्ट पर्सनल AI असिस्टेंट। मैं ऐप्स खोलने, YouTube, सर्च और नेविगेशन में आपकी मदद कर सकती हूँ।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.GENERAL_CHAT,
                    query = normalized,
                    responseText = reply,
                    category = MessageCategory.QUESTION,
                    confidence = 1.0f
                ),
                responseText = reply,
                category = MessageCategory.QUESTION,
                confidence = 1.0f
            )
        }

        return null
    }

    /**
     * Distinguishes Meta Instructions (how Myra should behave) from actual concrete actions.
     * Examples:
     * - "me chahta hu vo sare steps youtube par ho or vo tum khud kro"
     * - "main chahta hu tum ye kaam khud karo"
     * - "youtube par jo karna hai vo tum khud karo"
     * - "main chahta hu ki tum khud saare steps karo"
     * - "mujhe manually kuch nahi karna"
     * - "tum khud karo"
     * - "tum apne aap karo"
     */
    private fun checkMetaInstruction(normalized: String, original: String): LocalCommandResult? {
        val hasPreferenceClue = listOf("chahta hu", "chahti hu", "chahata hu", "want", "चाहता हूँ", "चाहती हूँ")
            .any { normalized.contains(it) }

        val hasAutonomousClue = listOf(
            "tum khud", "khud kro", "khud karo", "apne aap", "automatically", "manually kuch nahi",
            "tum khud hi", "khud se", "tum khud ye", "tum khud saare", "tum khud sare"
        ).any { normalized.contains(it) }

        val hasStepsClue = listOf("sare steps", "saare steps", "all steps", "jo karna hai", "jo bhi steps")
            .any { normalized.contains(it) }

        // If user says "tum khud karo" / "tum apne aap karo"
        val isDirectAutonomousInstruction = matchesAny(
            normalized,
            listOf(
                "tum khud karo", "tum khud kro", "tum apne aap karo", "tum khud karo na",
                "tum khud kar do", "khud karo", "apne aap karo", "tum khud ye kaam karo",
                "sare steps tum khud karo", "saare steps tum khud karo"
            )
        )

        val isMetaPattern = isDirectAutonomousInstruction ||
                (hasPreferenceClue && (hasAutonomousClue || hasStepsClue)) ||
                (hasAutonomousClue && hasStepsClue)

        if (!isMetaPattern) return null

        // Check if there is also a concrete action (e.g. video to play or app to launch)
        // Example: "main chahta hu tum khud YouTube kholo aur Desi Gamer ka video play karo"
        val hasPlayOrAppAction = listOf("play", "chalao", "chala do", "kholo", "search", "dhoondho", "khojo")
            .any { normalized.contains(it) }

        // Check if there is an actual video/song target (excluding the meta words themselves)
        val stripped = stripMetaWords(normalized)
            .replace("youtube", "")
            .replace("par", "")
            .replace("pe", "")
            .replace("mein", "")
            .replace("me", "")
            .replace("ho", "")
            .replace("or", "")
            .replace("aur", "")
            .replace("kro", "")
            .replace("karo", "")
            .trim()

        // If only meta words or no concrete query exists: Pure Meta Instruction!
        if (stripped.length <= 3 || stripped == "steps" || stripped == "sare" || stripped == "video" || stripped == "app") {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.META_INSTRUCTION,
                    metaInstruction = "ASSISTANT_SHOULD_PERFORM_AVAILABLE_STEPS_AUTOMATICALLY",
                    executionPreference = "PERFORM_STEPS_AUTOMATICALLY",
                    confidence = 1.0f,
                    category = MessageCategory.META_INSTRUCTION
                ),
                responseText = "समझ गई। अब से मैं उपलब्ध सभी स्टेप्स अपने आप (automatically) पूरे करने की कोशिश करूँगी।",
                category = MessageCategory.META_INSTRUCTION
            )
        }

        // If there IS a concrete action attached, let parseYouTubeCommand / appLauncher handle the action,
        // but with meta-instruction extracted and meta words stripped from the query!
        return null
    }

    /**
     * Detects Challenge / Autonomous Test Requests.
     * Example: "tumhari marji se koi random target pura karo jisme kam se kam 6 steps hona chahiye"
     */
    private fun checkChallengeRequest(normalized: String): LocalCommandResult? {
        val hasChallengeClue = listOf(
            "random target", "random task", "koi target", "koi random", "challenge",
            "apni marzi se", "tumhari marji se", "tumhari marzi se", "self test", "test task"
        ).any { normalized.contains(it) }

        val hasStepsOrActionClue = listOf(
            "step", "steps", "pura karo", "karo", "kro", "perform karo", "step hona", "steps hona"
        ).any { normalized.contains(it) }

        if (hasChallengeClue && hasStepsOrActionClue) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.CHALLENGE_REQUEST,
                    confidence = 1.0f,
                    category = MessageCategory.NEW_COMMAND
                ),
                responseText = "चैलेंज स्वीकार किया गया! 6-स्टेप्स का सुरक्षित ऑटोनॉमस वर्कफ़्लो निष्पादित किया जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        return null
    }

    private fun checkMultiCommand(trimmed: String): LocalCommandResult? {
        val splitRegex = Regex("(?i)\\s+(aur|और|and|phir|फिर)\\s+")
        val parts = trimmed.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val subResults = parts.map { parse(it) }
            if (subResults.all { it.handled && it.intent != null }) {
                val subIntents = subResults.map { it.intent!!.copy(category = it.category.takeIf { c -> c != MessageCategory.UNKNOWN } ?: MessageCategory.NEW_COMMAND) }
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(
                        type = IntentType.MULTI_ACTION,
                        actions = subIntents,
                        subIntents = subIntents,
                        confidence = 0.95f,
                        category = MessageCategory.NEW_COMMAND
                    ),
                    responseText = "दोनों कमांड प्रोसेस किए जा रहे हैं...",
                    category = MessageCategory.NEW_COMMAND
                )
            }
        }
        return null
    }

    private fun checkContextAndConversationQuestions(normalized: String): LocalCommandResult? {
        // A. RECALL_RECENT_REQUESTS
        val recallPhrases = listOf(
            "mene kya karne ko bola", "maine kya karne ko bola", "maine kya karne ko bola tha",
            "maine kya kaha tha", "maine kya bola", "mene kya bola", "maine kya bola tha",
            "maine tumhe kya karne ko bola tha", "what did i ask you to do", "what did i say",
            "what was my last command", "मैंने क्या बोला था", "मैंने क्या करने को बोला", "मैंने क्या कहा था",
            "mene kya bola tha", "maine kya pucha", "mene kya pucha", "maine kya kaha"
        )
        if (recallPhrases.any { normalized.contains(it) || it.contains(normalized) } ||
            normalized.matches(Regex("(?i)^(mene|maine|hamne|मैंने)\\s+(kya|tumhe\\s+kya).*"))
        ) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.RECALL_RECENT_REQUESTS,
                    confidence = 1.0f,
                    category = MessageCategory.CONTEXT_QUESTION
                ),
                responseText = "आपने हाल ही में दिए गए कमांड के बारे में पूछा है।",
                category = MessageCategory.CONTEXT_QUESTION
            )
        }

        // B. REPORT_LAST_EXECUTION & ITEM RESOLUTION ("1 wale me tumne kya kiya", "tumne kya kiya", "usme kya hua")
        val reportPhrases = listOf(
            "tumne kya kiya", "kya kiya tumne", "tumne kya kya kiya", "tumne kya kara",
            "hua kya", "kaam hua", "kya hua", "status kya hai", "kya status hai",
            "what did you do", "did it work", "तुमने क्या किया", "हुआ क्या", "काम हुआ",
            "kuch hua", "complete hua", "kya bana", "usme kya hua", "usme kya kiya"
        )

        val hasReportClue = reportPhrases.any {
            normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") || normalized.contains(it)
        }

        // Check if referencing a specific numbered item: e.g. "1 wale me tumne kya kiya", "2nd wale me kya hua"
        val indexMatch = Regex("(?i)\\b(1|2|3|4|5|pehla|pehle|pahla|pahle|dusra|dusre|doosra|doosre|teesra|teesre|first|second|third)\\s*(wale|waale|me|mein)?\\b")
            .find(normalized)

        val targetIndex = if (indexMatch != null) {
            when (indexMatch.groupValues[1].lowercase()) {
                "1", "pehla", "pehle", "pahla", "pahle", "first" -> 1
                "2", "dusra", "dusre", "doosra", "doosre", "second" -> 2
                "3", "teesra", "teesre", "third" -> 3
                "4" -> 4
                "5" -> 5
                else -> 1
            }
        } else null

        if (hasReportClue || targetIndex != null) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.REPORT_LAST_EXECUTION,
                    targetIndex = targetIndex,
                    referenceType = if (targetIndex != null) "INDEX" else "LAST",
                    confidence = 1.0f,
                    category = MessageCategory.EXECUTION_STATUS
                ),
                responseText = if (targetIndex != null) {
                    "अनुरोध #$targetIndex की स्थिति जाँची जा रही है।"
                } else {
                    "पिछले एक्शन की स्थिति जाँची जा रही है।"
                },
                category = MessageCategory.EXECUTION_STATUS
            )
        }

        // C. EXPLAIN_LAST_FAILURE
        val explainPhrases = listOf(
            "kyu nahi hua", "kyun nahi hua", "kyu fail hua", "fail kyu hua",
            "why did it fail", "why didn't it work", "क्यों नहीं हुआ", "फेल क्यों हुआ",
            "kyu ruk gaya", "kyun ruk gaya", "kaam kyu nahi hua"
        )
        if (explainPhrases.any { normalized.contains(it) }) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.EXPLAIN_LAST_FAILURE,
                    confidence = 1.0f,
                    category = MessageCategory.EXECUTION_STATUS
                ),
                responseText = "पिछले काम के पूरा न होने का कारण जाँचा जा रहा है।",
                category = MessageCategory.EXECUTION_STATUS
            )
        }

        // D. RETRY_LAST_REQUEST
        val retryPhrases = listOf(
            "dobara karo", "phir se karo", "fir se karo", "repeat karo", "repeat",
            "try again", "दोबारा करो", "फिर से करो", "wapas karo"
        )
        if (retryPhrases.any { normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") }) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(
                    type = IntentType.RETRY_LAST_REQUEST,
                    confidence = 1.0f,
                    category = MessageCategory.RETRY_REQUEST
                ),
                responseText = "पिछला अनुरोध फिर से दोहराया जा रहा है...",
                category = MessageCategory.RETRY_REQUEST
            )
        }

        return null
    }

    private fun parseYouTubeCommand(original: String, normalized: String): LocalCommandResult? {
        val hasYouTube = normalized.contains("youtube") || normalized.contains("yt") || normalized.contains("यूट्यूब")
        val hasVideo = normalized.contains("video") || normalized.contains("videos") || normalized.contains("वीडियो") ||
                normalized.contains("song") || normalized.contains("songs") || normalized.contains("गाना") || normalized.contains("गाने")

        val isPlayCommand = listOf(
            "play", "chalao", "chala do", "chala", "chalana", "bajaao", "bajao",
            "play karo", "play kro", "play kar do", "प्ले करो", "प्ले", "चलाओ", "चला दो", "चला", "बजाओ"
        ).any { normalized.contains(it) }

        val isSearchCommand = listOf(
            "search", "search karo", "search kro", "khojo", "dhoondho", "सर्च", "खोजो", "ढूंढो"
        ).any { normalized.contains(it) }

        if (!hasYouTube && !(hasVideo && isPlayCommand)) {
            return null
        }

        // Check if user just wants to open the app (no video/song query or search keyword)
        if (!isSearchCommand && !hasVideo) {
            val stripped = stripLaunchAndPoliteness(normalized, "youtube")
            if (stripped.isEmpty() || hasLaunchSemantics(normalized) || normalized == "youtube" || normalized == "open youtube") {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = "youtube", target = AppLauncher.PKG_YOUTUBE, confidence = 0.95f),
                    responseText = "YouTube ऐप खोला जा रहा है..."
                )
            }
        }

        // Clean extraction of query
        var query = original

        // Strip any meta instruction phrases if combined (e.g. "main chahta hu tum khud...")
        query = stripMetaWords(query)

        // 1. Remove youtube reference and platform prepositions
        query = query.replace(Regex("(?i)\\b(in\\s+youtube|on\\s+youtube|youtube\\s*(par|pe|mein|me|ko|on|in|per)?|yt\\s*(par|pe|mein|me)?|यूट्यूब\\s*(पर|में)?)\\b"), " ")

        // 2. Remove play action words from tail
        val playTailPattern = "(?i)\\s*(ka|ki|ke|का|की|के)?\\s*(video|videos|वीडियो|song|songs|gana|गाने)?\\s*(play\\s*(karo|kro|kar\\s*do)?|chalao|chala\\s*do|chala\\s*dena|chalana|bajaao|bajao|प्ले\\s*करो|प्ले\\s*कर\\s*दो|प्ले|चलाओ|चला\\s*दो|चला|बजाओ)\\s*$"
        query = query.replace(Regex(playTailPattern), " ")

        // 3. Remove play action words from head (e.g. "play Free Fire video on YouTube")
        val playHeadPattern = "(?i)^\\s*(play|chalao|chala\\s*do|bajaao|प्ले\\s*करो|चलाओ|प्ले)\\s*(video|videos|वीडियो|song|गाने)?\\s*(of|ka|ki|ke|का|की|के)?\\s*"
        query = query.replace(Regex(playHeadPattern), " ")

        // 4. Remove search action words from tail
        val searchTailPattern = "(?i)\\s*(ko|par|pe)?\\s*(search\\s*(karo|kro|kar\\s*do)?|khojo|dhoondho|dhoondo|सर्च\\s*करो|सर्च|खोजो|ढूंढो)\\s*$"
        query = query.replace(Regex(searchTailPattern), " ")

        // 5. Remove search action words from head
        val searchHeadPattern = "(?i)^\\s*(search|khojo|dhoondho|सर्च\\s*करो|खोजो)\\s*(for|ko)?\\s*"
        query = query.replace(Regex(searchHeadPattern), " ")

        // 6. Clean up lingering connectives and action fragments
        query = query.replace(Regex("(?i)^\\s*(par|pe|mein|me|on|in|ko|ka|ki|ke|का|की|के|पर|में)\\s+"), " ")
        query = query.replace(Regex("(?i)\\s+(par|pe|mein|me|on|in|ko|ka|ki|ke|का|की|के|पर|में)\\s*$"), " ")
        query = query.replace(Regex("(?i)\\b(kro|karo|करो|do|दो)\\b"), " ")
        query = query.replace(Regex("\\s+"), " ").trim()

        if (query.isBlank()) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_APP, app = "youtube", target = AppLauncher.PKG_YOUTUBE, confidence = 0.95f),
                responseText = "YouTube ऐप खोला जा रहा है..."
            )
        }

        val intentType = if (isPlayCommand || hasVideo) IntentType.SEARCH_AND_PLAY else IntentType.YOUTUBE_SEARCH

        return LocalCommandResult(
            handled = true,
            intent = ParsedIntent(
                type = intentType,
                app = "youtube",
                target = AppLauncher.PKG_YOUTUBE,
                query = query,
                targetType = "VIDEO",
                action = "PLAY",
                confidence = 0.98f,
                category = MessageCategory.NEW_COMMAND
            ),
            responseText = if (intentType == IntentType.SEARCH_AND_PLAY) {
                "YouTube पर '$query' का वीडियो चलाया जा रहा है..."
            } else {
                "YouTube पर '$query' खोजा जा रहा है..."
            },
            category = MessageCategory.NEW_COMMAND
        )
    }

    private fun parseChromeCommand(original: String, normalized: String): LocalCommandResult? {
        val hasChrome = normalized.contains("chrome") || normalized.contains("google chrome") || normalized.contains("browser")
        if (!hasChrome) return null

        var query = original
        query = query.replace(Regex("(?i)\\b(google\\s+chrome|chrome|browser)\\s*(mein|me|par|pe|in|on|पर|में)?\\b"), " ")
        query = query.replace(Regex("(?i)\\s*(kholo|open\\s*(karo|kro)?|open|chalao|dhoondho|khojo|search\\s*(karo|kro)?|सर्च\\s*करो|खोलो|सर्च)\\s*$"), " ")
        query = query.replace(Regex("(?i)^\\s*(kholo|open|search\\s*(for)?)\\s*"), " ")
        query = query.replace(Regex("\\s+"), " ").trim()

        if (query.isBlank()) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_APP, app = "chrome", target = AppLauncher.PKG_CHROME, confidence = 0.95f, category = MessageCategory.NEW_COMMAND),
                responseText = "Chrome ब्राउज़र खोला जा रहा है...",
                category = MessageCategory.NEW_COMMAND
            )
        }

        return LocalCommandResult(
            handled = true,
            intent = ParsedIntent(
                type = IntentType.OPEN_PAGE,
                app = "chrome",
                target = query,
                query = query,
                confidence = 0.95f,
                category = MessageCategory.NEW_COMMAND
            ),
            responseText = "Chrome में '$query' खोला जा रहा है...",
            category = MessageCategory.NEW_COMMAND
        )
    }

    fun normalizeText(raw: String): String {
        return raw.lowercase(Locale.getDefault())
            .replace(Regex("[,.!?_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun stripMetaWords(text: String): String {
        val metaPatterns = listOf(
            "(?i)\\b(main\\s+chahta\\s+hu|me\\s+chahta\\s+hu|chahta\\s+hu|chahti\\s+hu|i\\s+want)\\b",
            "(?i)\\b(tum\\s+khud|tum\\s+khud\\s+hi|khud\\s+kro|khud\\s+karo|khud|automatically|apne\\s+aap)\\b",
            "(?i)\\b(sare\\s+steps|saare\\s+steps|all\\s+steps|jo\\s+bhi\\s+steps|steps)\\b",
            "(?i)\\b(kripya|please|zara|mujhe)\\b",
            "(?i)\\b(vo|wo|jo|ki|or|aur)\\b"
        )
        var result = text
        for (p in metaPatterns) {
            result = result.replace(Regex(p), " ")
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    private fun matchesAny(text: String, patterns: List<String>): Boolean {
        return patterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }
    }

    private fun hasLaunchSemantics(text: String): Boolean {
        val launchWords = listOf(
            "kholo", "chalao", "open", "launch", "start", "chala do", "khol do", "kholkar do",
            "khol ke do", "open karo", "start karo", "खोलो", "चलाओ", "खोल दो", "चला दो", "शुरू करो", "चला"
        )
        return launchWords.any { text.contains(it) }
    }

    private fun checkAppLaunch(normalized: String, original: String): LocalCommandResult? {
        val hasLaunchWord = hasLaunchSemantics(normalized)

        val appMap = mapOf(
            "youtube" to AppLauncher.PKG_YOUTUBE,
            "chrome" to AppLauncher.PKG_CHROME,
            "google chrome" to AppLauncher.PKG_CHROME,
            "browser" to AppLauncher.PKG_CHROME,
            "maps" to AppLauncher.PKG_MAPS,
            "google maps" to AppLauncher.PKG_MAPS,
            "whatsapp" to AppLauncher.PKG_WHATSAPP
        )

        for ((name, pkg) in appMap) {
            if (normalized.contains(name)) {
                val stripped = stripLaunchAndPoliteness(normalized, name)
                if (stripped.isEmpty() || hasLaunchWord || normalized == name || normalized == "open $name") {
                    return LocalCommandResult(
                        handled = true,
                        intent = ParsedIntent(type = IntentType.OPEN_APP, app = name, target = pkg, confidence = 0.95f, category = MessageCategory.NEW_COMMAND),
                        responseText = "$name ऐप खोला जा रहा है...",
                        category = MessageCategory.NEW_COMMAND
                    )
                }
            }
        }

        if (hasLaunchWord) {
            var extractedApp = original
            val removalPatterns = listOf(
                "(?i)\\b(mujhe|kripya|please|zara|ek baar|app|application|kholo|chalao|open|launch|start|chala do|khol do|kholkar do|khol ke do|open karo|start karo|ko|खोलो|चलाओ|खोल दो|चला दो|शुरू करो|चला|दो)\\b"
            )
            for (p in removalPatterns) {
                extractedApp = extractedApp.replace(Regex(p), "")
            }
            val cleaned = extractedApp.replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F\\s]"), "").trim()
            if (cleaned.length in 2..30) {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = cleaned, confidence = 0.85f, category = MessageCategory.NEW_COMMAND),
                    responseText = "$cleaned ऐप खोला जा रहा है...",
                    category = MessageCategory.NEW_COMMAND
                )
            }
        }

        return null
    }

    private fun stripLaunchAndPoliteness(normalized: String, appName: String): String {
        return normalized.replace(appName, "")
            .replace(Regex("(?i)\\b(mujhe|kripya|please|zara|ek baar|app|kholo|chalao|open|launch|start|chala do|khol do|kholkar do|khol ke do|open karo|start karo|chala|do|ko|खोलो|चलाओ|खोल दो|चला दो|शुरू करो|चला|दो|कर|के|दीजिये|दीजिए)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractSearchQuery(text: String): String {
        return text.replace(Regex("(?i)\\b(search|google|karo|pe|par|on|for|dhoondho|batao|khojo|सर्च करो|ढूंढो|बताओ|खोजो)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
