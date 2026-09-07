package com.example.services

import com.example.models.ActionResult
import com.example.models.IntentType
import com.example.models.LocalCommandResult
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

        // 0. Check Multi-command (e.g. "... aur ...")
        val multiResult = checkMultiCommand(trimmed)
        if (multiResult != null) {
            return multiResult
        }

        // 1. Context Questions & Status Queries (Must be checked BEFORE search/actions)
        val contextResult = checkContextAndConversationQuestions(normalized)
        if (contextResult != null) {
            return contextResult
        }

        // 2. Cancel request
        if (matchesAny(normalized, listOf("cancel", "cancel karo", "rok do", "rehne do", "radd karo", "रद्द करो", "रहने दो", "रोक दो", "stop action", "stop"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.CANCEL_REQUEST),
                responseText = "कमांड रद्द कर दी गई है।"
            )
        }

        // 3. Clear chat
        if (matchesAny(normalized, listOf("clear chat", "chat clear karo", "chat saaf karo", "delete chat", "clear history", "saaf karo", "चैट साफ़ करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.CLEAR_CHAT),
                actionResult = ActionResult(success = true, message = "बातचीत साफ़ कर दी गई है (Chat cleared)"),
                responseText = "बातचीत साफ़ कर दी गई है। अब आप नया सवाल पूछ सकते हैं।"
            )
        }

        // 4. Greetings & Identity
        if (matchesAny(normalized, listOf("hi", "hello", "hey", "namaste", "नमस्ते", "हेलो", "myra", "myra kaun ho", "tum kaun ho", "who are you"))) {
            val reply = "नमस्ते! मैं Myra हूँ, आपकी पर्सनल AI असिस्टेंट। मैं ऐप्स खोलने, YouTube, सर्च और नेविगेशन में आपकी मदद कर सकती हूँ।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GENERAL_CHAT, responseText = reply),
                responseText = reply
            )
        }

        if (matchesAny(normalized, listOf("kaise ho", "how are you", "kya haal hai", "कैसी हो", "कैसे हो"))) {
            val reply = "मैं बिल्कुल ठीक हूँ! आपकी क्या मदद करूँ? आप कह सकते हैं: 'YouTube खोलो', 'Camera खोलो', या 'Google पर सर्च करो'।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GENERAL_CHAT, responseText = reply),
                responseText = reply
            )
        }

        // 5. Navigation: Back / Home / Recents
        if (matchesAny(normalized, listOf("go back", "back jao", "back", "peeche jao", "wapas jao", "wapas", "पीछे जाओ", "वापस"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GO_BACK),
                responseText = "वापस जाया जा रहा है..."
            )
        }

        if (matchesAny(normalized, listOf("go home", "home screen", "home jao", "ghar jao", "home", "होम स्क्रीन", "होम जाओ", "होम"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GO_HOME),
                responseText = "होम स्क्रीन पर जाया जा रहा है..."
            )
        }

        // 6. Scroll navigation
        if (matchesAny(normalized, listOf("scroll down", "neeche karo", "neeche scroll karo", "neeche jao", "नीचे करो", "नीचे स्क्रॉल करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.SCROLL_DOWN),
                responseText = "नीचे स्क्रॉल किया जा रहा है..."
            )
        }

        if (matchesAny(normalized, listOf("scroll up", "upar karo", "upar scroll karo", "upar jao", "ऊपर करो", "ऊपर स्क्रॉल करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.SCROLL_UP),
                responseText = "ऊपर स्क्रॉल किया जा रहा है..."
            )
        }

        // 7. Read screen
        if (matchesAny(normalized, listOf("read screen", "screen padho", "screen par kya hai", "kya dikh raha hai", "स्क्रीन पढ़ो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.READ_SCREEN),
                responseText = "स्क्रीन की जानकारी पढ़ी जा रही है..."
            )
        }

        // 8. Media controls: Play / Pause / Stop
        val isPauseCommand = listOf("pause video", "pause song", "pause", "video roko", "gaana roko", "ruk jao", "पॉज़ करो", "रोको")
            .any { normalized == it || normalized.startsWith("$it ") }
        if (isPauseCommand) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.PAUSE, target = "pause"),
                responseText = "पॉज़ किया जा रहा है..."
            )
        }

        val isDirectPlayCommand = (normalized == "play" || normalized == "chalao" || normalized == "चलाओ" ||
                normalized == "resume" || normalized == "play video" || normalized == "video chalao")
        if (isDirectPlayCommand) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.PLAY, target = "play"),
                responseText = "प्ले किया जा रहा है..."
            )
        }

        // 9. Camera
        if (normalized.contains("camera") || normalized.contains("कैमरा") || normalized.contains("photo khincho") || normalized.contains("फोटो खींचो")) {
            if (hasLaunchSemantics(normalized) || normalized == "camera" || normalized == "कैमरा") {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = "camera"),
                    responseText = "कैमरा खोला जा रहा है..."
                )
            }
        }

        // 10. Dialer
        if (normalized.contains("dialer") || normalized.contains("phone dialer") || normalized.contains("डायलर") || normalized.contains("call lagao") || normalized.contains("phone milao")) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_APP, app = "dialer"),
                responseText = "फोन डायलर खोला जा रहा है..."
            )
        }

        // 11. Settings
        if (normalized.contains("settings") || normalized.contains("सेटिंग्स") || normalized.contains("setting")) {
            if (hasLaunchSemantics(normalized) || normalized == "settings" || normalized == "setting") {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_SETTINGS),
                    responseText = "सिस्टम सेटिंग्स खोली जा रही हैं..."
                )
            }
        }

        // 12. YouTube Search & Play (With robust query extraction)
        val ytResult = parseYouTubeCommand(trimmed, normalized)
        if (ytResult != null) {
            return ytResult
        }

        // 13. Chrome & Specific Web Page opening
        val chromeResult = parseChromeCommand(trimmed, normalized)
        if (chromeResult != null) {
            return chromeResult
        }

        // 14. Check App Launch Intent for installed apps
        val appLaunchResult = checkAppLaunch(normalized, trimmed)
        if (appLaunchResult != null) {
            return appLaunchResult
        }

        // 15. Direct URL
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || (normalized.startsWith("www.") && normalized.contains("."))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_URL, target = trimmed),
                responseText = "वेबसाइट खोली जा रही है: $trimmed"
            )
        }

        // 16. Web Search
        if (normalized.startsWith("search ") || normalized.startsWith("google ") || normalized.contains("search karo") || normalized.contains("सर्च करो") || normalized.contains("dhoondho") || normalized.contains("khojo")) {
            val query = extractSearchQuery(trimmed)
            if (query.isNotEmpty()) {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.WEB_SEARCH, query = query),
                    responseText = "Google पर '$query' खोजा जा रहा है..."
                )
            }
        }

        return LocalCommandResult(handled = false, reason = "No local pattern matched")
    }

    private fun checkMultiCommand(trimmed: String): LocalCommandResult? {
        val splitRegex = Regex("(?i)\\s+(aur|और|and|phir|फिर)\\s+")
        val parts = trimmed.split(splitRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val subResults = parts.map { parse(it) }
            if (subResults.all { it.handled && it.intent != null }) {
                val subIntents = subResults.map { it.intent!! }
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(
                        type = IntentType.MULTI_ACTION,
                        actions = subIntents,
                        subIntents = subIntents,
                        confidence = 0.95f
                    ),
                    responseText = "दोनों कमांड प्रोसेस किए जा रहे हैं..."
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
                intent = ParsedIntent(type = IntentType.RECALL_RECENT_REQUESTS, confidence = 1.0f),
                responseText = "आपने हाल ही में दिए गए कमांड के बारे में पूछा है।"
            )
        }

        // B. REPORT_LAST_EXECUTION
        val reportPhrases = listOf(
            "tumne kya kiya", "kya kiya tumne", "tumne kya kya kiya", "tumne kya kara",
            "hua kya", "kaam hua", "kya hua", "status kya hai", "kya status hai",
            "what did you do", "did it work", "तुमने क्या किया", "हुआ क्या", "काम हुआ",
            "kuch hua", "complete hua", "kya bana"
        )
        if (reportPhrases.any { normalized == it || normalized.startsWith("$it ") || normalized.endsWith(" $it") || normalized.contains(it) }) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.REPORT_LAST_EXECUTION, confidence = 1.0f),
                responseText = "पिछले एक्शन की स्थिति जाँची जा रही है।"
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
                intent = ParsedIntent(type = IntentType.EXPLAIN_LAST_FAILURE, confidence = 1.0f),
                responseText = "पिछले काम के पूरा न होने का कारण जाँचा जा रहा है।"
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
                intent = ParsedIntent(type = IntentType.RETRY_LAST_REQUEST, confidence = 1.0f),
                responseText = "पिछला अनुरोध फिर से दोहराया जा रहा है..."
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
                confidence = 0.98f
            ),
            responseText = if (intentType == IntentType.SEARCH_AND_PLAY) {
                "YouTube पर '$query' का वीडियो चलाया जा रहा है..."
            } else {
                "YouTube पर '$query' खोजा जा रहा है..."
            }
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
                intent = ParsedIntent(type = IntentType.OPEN_APP, app = "chrome", target = AppLauncher.PKG_CHROME, confidence = 0.95f),
                responseText = "Chrome ब्राउज़र खोला जा रहा है..."
            )
        }

        return LocalCommandResult(
            handled = true,
            intent = ParsedIntent(
                type = IntentType.OPEN_PAGE,
                app = "chrome",
                target = query,
                query = query,
                confidence = 0.95f
            ),
            responseText = "Chrome में '$query' खोला जा रहा है..."
        )
    }

    fun normalizeText(raw: String): String {
        return raw.lowercase(Locale.getDefault())
            .replace(Regex("[,.!?_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
                        intent = ParsedIntent(type = IntentType.OPEN_APP, app = name, target = pkg, confidence = 0.95f),
                        responseText = "$name ऐप खोला जा रहा है..."
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
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = cleaned, confidence = 0.85f),
                    responseText = "$cleaned ऐप खोला जा रहा है..."
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

