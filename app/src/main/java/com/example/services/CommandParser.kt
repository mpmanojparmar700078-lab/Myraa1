package com.example.services

import com.example.models.IntentType
import com.example.models.ParsedIntent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class CommandParser(
    private val localCommandParser: LocalCommandParser = LocalCommandParser()
) {

    /**
     * Level 1 Local Deterministic Command Parsing.
     * Returns ParsedIntent if recognized by LocalCommandParser with 100% confidence, or null to delegate to Gemini AI.
     */
    fun parseDeterministicCommand(input: String): ParsedIntent? {
        val result = localCommandParser.parse(input)
        return if (result.recognized) {
            result.parsedIntent
        } else {
            null
        }
    }

    /**
     * Parses structured JSON output from Gemini AI into a validated ParsedIntent.
     * Supports both single intent schema and multi_action sequence plans.
     */
    fun parseGeminiJsonResponse(rawJsonText: String): ParsedIntent {
        try {
            var cleanText = rawJsonText.trim()
            if (cleanText.startsWith("```json")) {
                cleanText = cleanText.removePrefix("```json")
            }
            if (cleanText.startsWith("```")) {
                cleanText = cleanText.removePrefix("```")
            }
            if (cleanText.endsWith("```")) {
                cleanText = cleanText.removeSuffix("```")
            }
            cleanText = cleanText.trim()

            val firstBrace = cleanText.indexOf('{')
            val lastBrace = cleanText.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleanText = cleanText.substring(firstBrace, lastBrace + 1)
            }

            val json = JSONObject(cleanText)

            // Check if this is a Multi-Action plan
            val taskType = json.optString("task", "").lowercase(Locale.ROOT)
            val actionsArray = json.optJSONArray("actions")
            val isMultiAction = taskType == "multi_action" || (actionsArray != null && actionsArray.length() > 1)

            if (isMultiAction && actionsArray != null && actionsArray.length() > 0) {
                val parsedSubActions = mutableListOf<ParsedIntent>()
                for (i in 0 until actionsArray.length()) {
                    val subObj = actionsArray.optJSONObject(i) ?: continue
                    val subTypeStr = (subObj.optString("type", "").takeIf { it.isNotBlank() }
                        ?: subObj.optString("intent", "")).uppercase(Locale.ROOT)

                    val subType = mapStringToIntentType(subTypeStr)
                    if (subType == IntentType.UNKNOWN) continue // Skip unsafe or invalid action types

                    val subApp = subObj.optString("app", "").takeIf { it.isNotBlank() }
                    val subQuery = subObj.optString("query", "").takeIf { it.isNotBlank() }
                    val subTarget = subObj.optString("target", "").takeIf { it.isNotBlank() }
                    val subDuration = subObj.optLong("durationMs", 1000L)
                    val subKey = subObj.optString("key", "").takeIf { it.isNotBlank() }
                    val subValue = subObj.optString("value", "").takeIf { it.isNotBlank() }

                    parsedSubActions.add(
                        ParsedIntent(
                            type = subType,
                            app = subApp,
                            query = subQuery,
                            target = subTarget,
                            durationMs = subDuration,
                            key = subKey,
                            value = subValue
                        )
                    )
                }

                if (parsedSubActions.isNotEmpty()) {
                    val responseText = json.optString("responseText", "").takeIf { it.isNotBlank() }
                        ?: json.optString("response", "Okay, executing sequential actions.")

                    return ParsedIntent(
                        type = IntentType.MULTI_ACTION,
                        actions = parsedSubActions,
                        responseText = responseText
                    )
                }
            }

            // Single Action or General Chat
            val intentStr = (json.optString("intent", "").takeIf { it.isNotBlank() }
                ?: json.optString("type", "GENERAL_CHAT")).uppercase(Locale.ROOT)

            val app = json.optString("app", "").takeIf { it.isNotBlank() }
            val query = json.optString("query", "").takeIf { it.isNotBlank() }
            val target = json.optString("target", "").takeIf { it.isNotBlank() }
            val key = json.optString("key", "").takeIf { it.isNotBlank() }
            val value = json.optString("value", "").takeIf { it.isNotBlank() }
            val duration = json.optLong("durationMs", 1000L)
            val responseText = json.optString("responseText", "").takeIf { it.isNotBlank() }
                ?: json.optString("response", "Command processed.")

            val type = mapStringToIntentType(intentStr)

            return ParsedIntent(
                type = type,
                app = app,
                query = query,
                target = target,
                key = key,
                value = value,
                durationMs = duration,
                responseText = responseText
            )
        } catch (e: Exception) {
            // If response was not valid JSON, treat the entire string safely as general chat reply
            return ParsedIntent(
                type = IntentType.GENERAL_CHAT,
                responseText = rawJsonText.trim()
            )
        }
    }

    private fun mapStringToIntentType(rawType: String): IntentType {
        return when (rawType) {
            "OPEN_APP" -> IntentType.OPEN_APP
            "OPEN_SETTINGS" -> IntentType.OPEN_SETTINGS
            "WEB_SEARCH" -> IntentType.WEB_SEARCH
            "YOUTUBE_SEARCH" -> IntentType.YOUTUBE_SEARCH
            "YOUTUBE_SEARCH_AND_PLAY" -> IntentType.YOUTUBE_SEARCH_AND_PLAY
            "OPEN_URL" -> IntentType.OPEN_URL
            "SET_PREFERENCE" -> IntentType.SET_PREFERENCE
            "WAIT" -> IntentType.WAIT
            "BACK" -> IntentType.BACK
            "CLEAR_CHAT" -> IntentType.CLEAR_CHAT
            "GENERAL_CHAT" -> IntentType.GENERAL_CHAT
            else -> IntentType.UNKNOWN
        }
    }
}
