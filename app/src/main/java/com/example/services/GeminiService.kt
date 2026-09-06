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
        val apiKey = getCustomApiKey().ifBlank { BuildConfig.GEMINI_API_KEY }

        if (apiKey.isBlank()) {
            return@withContext generateOfflineFallback(prompt)
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

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

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext generateOfflineFallback(prompt)
                }

                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "उत्तर प्राप्त नहीं हो सका।")
                    }
                }
                generateOfflineFallback(prompt)
            }
        } catch (e: Exception) {
            generateOfflineFallback(prompt)
        }
    }

    private fun generateOfflineFallback(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("weather") || lower.contains("मौसम") ->
                "मौसम की ताज़ा जानकारी के लिए आप 'Search weather today' कह सकते हैं या Google मौसम देख सकते हैं।"
            lower.contains("time") || lower.contains("समय") || lower.contains("samay") ->
                "वर्तमान समय आपके डिवाइस स्टेटस बार में ऊपर प्रदर्शित है।"
            lower.contains("help") || lower.contains("madad") || lower.contains("मदद") ->
                "मैं आपकी इन चीज़ों में मदद कर सकती हूँ:\n• 'YouTube खोलो'\n• 'Camera खोलो'\n• 'Google पर सर्च करो'\n• 'Settings खोलो'\n• सवाल पूछें या बातचीत करें"
            else ->
                "मैंने आपका संदेश समझ लिया है: \"$prompt\"। आप चाहें तो सेटिंग्स में अपनी Gemini API Key जोड़कर विस्तृत AI उत्तर प्राप्त कर सकते हैं।"
        }
    }
}
