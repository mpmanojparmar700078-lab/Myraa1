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

        val lower = trimmed.lowercase(Locale.getDefault())

        // Clear chat commands
        if (lower in listOf("clear chat", "chat clear karo", "chat saaf karo", "delete chat", "clear history", "saaf karo")) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.CLEAR_CHAT),
                actionResult = ActionResult(success = true, message = "बातचीत साफ़ कर दी गई है (Chat cleared)"),
                responseText = "बातचीत साफ़ कर दी गई है। अब आप नया सवाल पूछ सकते हैं।"
            )
        }

        // Greetings & Assistant identity
        if (lower in listOf("hi", "hello", "hey", "namaste", "नमस्ते", "हेलो", "हेलो", "myra", "myra kaun ho")) {
            val reply = "नमस्ते! मैं Myra हूँ, आपकी पर्सनल AI असिस्टेंट। मैं ऐप्स खोलने, वेब सर्च, YouTube और रोज़मर्रा के कामों में आपकी मदद कर सकती हूँ।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GENERAL_CHAT, responseText = reply),
                actionResult = ActionResult(success = true, message = reply),
                responseText = reply
            )
        }

        if (lower.contains("kaise ho") || lower.contains("how are you") || lower.contains("कैसी हो") || lower.contains("कैसे हो")) {
            val reply = "मैं बिल्कुल ठीक हूँ! आपकी क्या मदद करूँ? आप कह सकते हैं: 'YouTube खोलो', 'Camera खोलो', या 'Google पे सर्च करो'।"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GENERAL_CHAT, responseText = reply),
                actionResult = ActionResult(success = true, message = reply),
                responseText = reply
            )
        }

        if (lower.contains("who are you") || lower.contains("tum kaun ho") || lower.contains("तुम कौन हो")) {
            val reply = "मैं Myra हूँ — आपका स्मार्ट और तेज़ Android AI असिस्टेंट!"
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.GENERAL_CHAT, responseText = reply),
                actionResult = ActionResult(success = true, message = reply),
                responseText = reply
            )
        }

        // Camera
        if (lower.contains("camera") || lower.contains("कैमरा") || lower.contains("photo khincho") || lower.contains("फोटो खींचो")) {
            if (lower.contains("open") || lower.contains("kholo") || lower.contains("chalao") || lower.contains("start") || lower.contains("खोलो")) {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = "camera"),
                    responseText = "कैमरा खोला जा रहा है..."
                )
            }
        }

        // Dialer
        if (lower.contains("dialer") || lower.contains("phone dialer") || lower.contains("डायलर") || lower.contains("call lagao") || lower.contains("phone milao")) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_APP, app = "dialer"),
                responseText = "फोन डायलर खोला जा रहा है..."
            )
        }

        // Settings
        if (lower.contains("settings") || lower.contains("सेटिंग्स") || lower.contains("setting")) {
            if (lower.contains("open") || lower.contains("kholo") || lower.contains("खोलो")) {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_SETTINGS),
                    responseText = "सिस्टम सेटिंग्स खोली जा रही हैं..."
                )
            }
        }

        // YouTube search and play
        if (lower.contains("youtube") && (lower.contains("search") || lower.contains("play") || lower.contains("video") || lower.contains("chalao") || lower.contains("dekho") || lower.contains("चलाओ"))) {
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
            }
        }

        // App Launch Intent Check
        val appLaunchResult = checkAppLaunch(lower, trimmed)
        if (appLaunchResult != null) {
            return appLaunchResult
        }

        // Direct URL
        if (lower.startsWith("http://") || lower.startsWith("https://") || (lower.startsWith("www.") && lower.contains("."))) {
            return LocalCommandResult(
                handled = true,
                intent = ParsedIntent(type = IntentType.OPEN_URL, target = trimmed),
                responseText = "वेबसाइट खोली जा रही है: $trimmed"
            )
        }

        // Web search
        if (lower.startsWith("search ") || lower.startsWith("google ") || lower.contains("search karo") || lower.contains("सर्च करो") || lower.contains("dhoondho")) {
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

    private fun checkAppLaunch(lower: String, original: String): LocalCommandResult? {
        val launchWords = listOf("open", "launch", "start", "kholo", "chalao", "खोलो", "चलाओ", "खोल दो", "chala do")
        val hasLaunchWord = launchWords.any { lower.contains(it) }

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
            if (lower.contains(name)) {
                if (hasLaunchWord || lower == name) {
                    return LocalCommandResult(
                        handled = true,
                        intent = ParsedIntent(type = IntentType.OPEN_APP, app = name, target = pkg),
                        responseText = "$name खोला जा रहा है..."
                    )
                }
            }
        }

        if (hasLaunchWord) {
            var extractedApp = original
            for (w in launchWords) {
                extractedApp = extractedApp.replace(Regex("(?i)\\b$w\\b"), "")
            }
            val cleaned = extractedApp.replace(Regex("(?i)app|application|please|kripya|kripya karke|ko"), "").trim()
            if (cleaned.length in 2..30) {
                return LocalCommandResult(
                    handled = true,
                    intent = ParsedIntent(type = IntentType.OPEN_APP, app = cleaned),
                    responseText = "$cleaned ऐप खोला जा रहा है..."
                )
            }
        }

        return null
    }

    private fun extractYouTubeQuery(text: String): String {
        return text.replace(Regex("(?i)\\b(youtube|open|search|play|video|chalao|kholo|dekho|pe|par|on|for|me|ko|please|खोलो|चलाओ)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractSearchQuery(text: String): String {
        return text.replace(Regex("(?i)\\b(search|google|karo|pe|par|on|for|dhoondho|batao|सर्च करो|ढूंढो|बताओ)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
