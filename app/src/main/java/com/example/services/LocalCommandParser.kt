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

        // 1. Cancel request
        if (matchesAny(normalized, listOf("cancel", "cancel karo", "rok do", "rehne do", "radd karo", "रद्द करो", "रहने दो", "रोक दो", "stop action"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.CANCEL_REQUEST),
                responseText = "कमांड रद्द कर दी गई है।"
            )
        }

        // 2. Clear chat
        if (matchesAny(normalized, listOf("clear chat", "chat clear karo", "chat saaf karo", "delete chat", "clear history", "saaf karo", "चैट साफ़ करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.CLEAR_CHAT),
                actionResult = ActionResult(success = true, message = "बातचीत साफ़ कर दी गई है (Chat cleared)"),
                responseText = "बातचीत साफ़ कर दी गई है। अब आप नया सवाल पूछ सकते हैं।"
            )
        }

        // 3. Repeat last action
        if (matchesAny(normalized, listOf("repeat", "repeat karo", "fir se karo", "phir se karo", "dobara karo", "pichla action", "दोबारा करो", "फिर से करो"))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.REPEAT_LAST_ACTION),
                responseText = "पिछला एक्शन दोहराया जा रहा है..."
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

        val isPlayCommand = listOf("play video", "play song", "resume", "gaana chalao", "video chalao", "bajaao", "प्ले करो")
            .any { normalized == it || normalized.startsWith("$it ") } || (normalized == "play" || normalized == "chalao" || normalized == "चलाओ")
        if (isPlayCommand) {
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

        // 12. Check App Launch Intent FIRST (Including YouTube Launch)
        val appLaunchResult = checkAppLaunch(normalized, trimmed)
        if (appLaunchResult != null) {
            return appLaunchResult
        }

        // 13. YouTube Search & Play (Only if actual search terms remain)
        if (normalized.contains("youtube")) {
            val cleanQuery = extractYouTubeQuery(trimmed)
            if (cleanQuery.isNotEmpty()) {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(
                        type = IntentType.YOUTUBE_SEARCH,
                        app = "youtube",
                        query = cleanQuery
                    ),
                    responseText = "YouTube पर '$cleanQuery' खोजा जा रहा है..."
                )
            } else {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = "youtube", target = AppLauncher.PKG_YOUTUBE, confidence = 0.95f),
                    responseText = "YouTube ऐप खोला जा रहा है..."
                )
            }
        }

        // 14. Direct URL
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || (normalized.startsWith("www.") && normalized.contains("."))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_URL, target = trimmed),
                responseText = "वेबसाइट खोली जा रही है: $trimmed"
            )
        }

        // 15. Web Search
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

    fun normalizeText(raw: String): String {
        return raw.lowercase(Locale.getDefault())
            .replace(Regex("[,.!?_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun matchesAny(text: String, patterns: List<String>): Boolean {
        return patterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") || text == it }
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
                // If the remainder is just launch/politeness words, it's definitely OPEN_APP
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

    private fun extractYouTubeQuery(text: String): String {
        return text.replace(Regex("(?i)\\b(youtube|open|search|play|video|chalao|kholo|dekho|pe|par|on|for|me|ko|please|mein|dalo|karo|chala|do|खोलो|चलाओ|खोजो|डालो|सर्च|चला|दो|कर|के)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractSearchQuery(text: String): String {
        return text.replace(Regex("(?i)\\b(search|google|karo|pe|par|on|for|dhoondho|batao|khojo|सर्च करो|ढूंढो|बताओ|खोजो)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
