package com.example.services

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService(private val getCustomApiKey: () -> String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateResponse(prompt: String, conversationContext: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = getCustomApiKey().trim().ifBlank { BuildConfig.GEMINI_API_KEY.trim() }

        if (apiKey.isBlank()) {
            return@withContext generateOfflineFallback(prompt, hasCustomKey = false)
        }

        // Try gemini-2.0-flash first, then gemini-1.5-flash
        val models = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.5-flash")
        var lastErrorMessage: String? = null

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

                val systemInstruction = "You are Myra, a helpful, friendly, and fast AI Assistant for Android. You speak Hindi, Hinglish, and English naturally based on user input. Keep responses concise, direct, helpful, and formatted for mobile screens."

                val requestJson = JSONObject().apply {
                    val contents = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                val combinedPrompt = if (conversationContext.isNotBlank()) {
                                    "Context: $conversationContext\nUser request: $prompt"
                                } else {
                                    prompt
                                }
                                put(JSONObject().apply { put("text", combinedPrompt) })
                            })
                        })
                    }
                    put("contents", contents)

                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                }

                val body = requestJson.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val responseJson = JSONObject(responseBody)
                    val candidates = responseJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val text = parts.getJSONObject(0).optString("text", "").trim()
                            if (text.isNotEmpty()) return@withContext text
                        }
                    }
                } else {
                    val errJson = try { JSONObject(responseBody).optJSONObject("error") } catch (e: Exception) { null }
                    val message = errJson?.optString("message") ?: "HTTP ${response.code}"
                    lastErrorMessage = "[$model] $message"
                    if (response.code == 400 || response.code == 403) {
                        return@withContext "Gemini API त्रुटि (${response.code}): $message\n\nकृपया Google AI Studio (aistudio.google.com) से अपनी सही Gemini API Key चेक करें।"
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = e.localizedMessage
            }
        }

        if (lastErrorMessage != null) {
            return@withContext "Gemini कनेक्ट करने में असमर्थ ($lastErrorMessage)। कृपया इंटरनेट और API Key जांचें।"
        }

        generateOfflineFallback(prompt, hasCustomKey = true)
    }

    private fun generateOfflineFallback(prompt: String, hasCustomKey: Boolean): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("weather") || lower.contains("मौसम") ->
                "मौसम की ताज़ा जानकारी के लिए आप 'Search weather today' कह सकते हैं या Google मौसम देख सकते हैं।"
            lower.contains("time") || lower.contains("समय") || lower.contains("samay") ->
                "वर्तमान समय आपके डिवाइस स्टेटस बार में ऊपर प्रदर्शित है।"
            lower.contains("help") || lower.contains("madad") || lower.contains("मदद") ->
                "मैं आपकी इन चीज़ों में मदद कर सकती हूँ:\n• 'YouTube खोलो'\n• 'Camera खोलो'\n• 'Google पर सर्च करो'\n• 'Settings खोलो'\n• सवाल पूछें या बातचीत करें"
            hasCustomKey ->
                "मैंने आपका संदेश समझ लिया है: \"$prompt\"। स्थानीय ब्रेन द्वारा इसे प्रोसेस किया गया।"
            else ->
                "मैंने आपका संदेश समझ लिया है: \"$prompt\"। आप चाहें तो सेटिंग्स में अपनी Gemini API Key जोड़कर विस्तृत AI उत्तर प्राप्त कर सकते हैं।"
        }
    }
}
