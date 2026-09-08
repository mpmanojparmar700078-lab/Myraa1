package com.example.services

import android.util.Log
import com.example.BuildConfig
import com.example.models.ApiKeyStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class GeminiService(private val getCustomApiKey: () -> String) {

    companion object {
        private const val TAG = "MYRA_GEMINI"
        private const val MAX_5XX_RETRIES = 2
        private const val MAX_429_RETRIES = 2
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getApiKeyStatus(): ApiKeyStatus {
        return try {
            val customKey = getCustomApiKey().trim()
            val buildKey = try { BuildConfig.GEMINI_API_KEY.trim() } catch (e: Throwable) { "" }
            when {
                customKey.isNotBlank() && customKey != "null" -> ApiKeyStatus.CONFIGURED
                buildKey.isNotBlank() && buildKey != "null" -> ApiKeyStatus.CONFIGURED
                else -> ApiKeyStatus.NOT_CONFIGURED
            }
        } catch (e: Exception) {
            ApiKeyStatus.UNKNOWN
        }
    }

    fun isConfigured(): Boolean = getApiKeyStatus() == ApiKeyStatus.CONFIGURED

    suspend fun generateResponse(prompt: String, conversationContext: String = ""): String = withContext(Dispatchers.IO) {
        val apiKey = getCustomApiKey().trim().ifBlank { BuildConfig.GEMINI_API_KEY.trim() }

        if (apiKey.isBlank()) {
            return@withContext generateOfflineFallback(prompt, hasCustomKey = false)
        }

        val modelsToTry = GeminiConfig.FALLBACK_MODELS
        var lastErrorDetails: GeminiErrorDetails? = null

        for (model in modelsToTry) {
            currentCoroutineContext().ensureActive()

            Log.d(TAG, "Model = $model")
            Log.d(TAG, "Request started")

            var attempt429 = 0
            var attempt5xx = 0

            while (true) {
                currentCoroutineContext().ensureActive()

                try {
                    val url = GeminiConfig.getGenerateContentUrl(model, apiKey)

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
                    val code = response.code
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        Log.d(TAG, "Response = $code")
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
                        // If empty candidates, break and try next or fallback
                        break
                    } else {
                        val errorDetails = parseErrorResponse(code, responseBody)
                        lastErrorDetails = errorDetails
                        Log.d(TAG, "HTTP $code Reason = ${errorDetails.type}")

                        when (errorDetails.type) {
                            GeminiErrorType.MODEL_NOT_FOUND -> {
                                // 404: Do not endlessly retry this model, break to try next fallback model
                                break
                            }
                            GeminiErrorType.INVALID_API_KEY -> {
                                // 400/401/403: Bad credentials, do not retry
                                return@withContext "Gemini API त्रुटि ($code - INVALID_API_KEY): ${errorDetails.userFriendlyMessage}\n\nकृपया Google AI Studio (aistudio.google.com) से अपनी सही Gemini API Key चेक करें।"
                            }
                            GeminiErrorType.RATE_LIMITED -> {
                                if (attempt429 < MAX_429_RETRIES) {
                                    attempt429++
                                    val jitter = Random.nextLong(200, 600)
                                    val backoffMs = (1000L * attempt429) + jitter
                                    Log.d(TAG, "Rate limited (429). Retrying attempt $attempt429 in ${backoffMs}ms...")
                                    delay(backoffMs)
                                    continue
                                } else {
                                    break
                                }
                            }
                            GeminiErrorType.SERVER_ERROR -> {
                                if (attempt5xx < MAX_5XX_RETRIES) {
                                    attempt5xx++
                                    val backoffMs = 1200L * attempt5xx
                                    Log.d(TAG, "Server error ($code). Retrying attempt $attempt5xx in ${backoffMs}ms...")
                                    delay(backoffMs)
                                    continue
                                } else {
                                    break
                                }
                            }
                            else -> {
                                break
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "Request cancelled by user or lifecycle.")
                    throw e
                } catch (e: IOException) {
                    lastErrorDetails = GeminiErrorDetails(
                        type = GeminiErrorType.NETWORK_ERROR,
                        httpCode = null,
                        rawMessage = e.localizedMessage ?: "Network error",
                        userFriendlyMessage = "इंटरनेट कनेक्शन जांचें।"
                    )
                    Log.d(TAG, "Network error: ${e.localizedMessage}")
                    break
                } catch (e: Exception) {
                    lastErrorDetails = GeminiErrorDetails(
                        type = GeminiErrorType.UNKNOWN_ERROR,
                        httpCode = null,
                        rawMessage = e.localizedMessage ?: "Unknown error",
                        userFriendlyMessage = e.localizedMessage ?: "त्रुटि"
                    )
                    Log.d(TAG, "Unexpected error: ${e.localizedMessage}")
                    break
                }
            }
        }

        if (lastErrorDetails != null) {
            return@withContext "Gemini कनेक्ट करने में समस्या (${lastErrorDetails.type}): ${lastErrorDetails.userFriendlyMessage}"
        }

        generateOfflineFallback(prompt, hasCustomKey = true)
    }

    suspend fun validateApiKey(testKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = testKey.trim()
        if (trimmed.isBlank()) {
            return@withContext Pair(false, "API Key खाली है। कृपया Google AI Studio से अपनी key डालें।")
        }

        val testModel = GeminiConfig.DEFAULT_MODEL
        Log.d(TAG, "Model = $testModel")
        Log.d(TAG, "Request started (Validation)")

        try {
            val url = GeminiConfig.getGenerateContentUrl(testModel, trimmed)
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Say OK") })
                        })
                    })
                }
                put("contents", contents)
            }
            val body = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(body).build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.d(TAG, "Response = 200 (Validation Success)")
                    Pair(true, "API Key मान्य (Valid) है और सक्रिय रूप से कनेक्ट हो गई! (Model: $testModel)")
                } else {
                    val errorDetails = parseErrorResponse(code, responseBody)
                    Log.d(TAG, "HTTP $code Reason = ${errorDetails.type}")

                    val msg = when (errorDetails.type) {
                        GeminiErrorType.MODEL_NOT_FOUND ->
                            "अमान्य मॉडल ($code - MODEL_NOT_FOUND): मॉडल '$testModel' उपलब्ध नहीं है।"
                        GeminiErrorType.INVALID_API_KEY ->
                            "अमान्य API Key ($code - INVALID_API_KEY): ${errorDetails.userFriendlyMessage}"
                        GeminiErrorType.RATE_LIMITED ->
                            "रेट लिमिट ($code - RATE_LIMITED): API कोटा समाप्त हो गया है। कृपया बाद में प्रयास करें।"
                        GeminiErrorType.SERVER_ERROR ->
                            "सर्वर त्रुटि ($code - SERVER_ERROR): Google सर्वर अस्थायी रूप से अनुपलब्ध है।"
                        GeminiErrorType.NETWORK_ERROR ->
                            "नेटवर्क त्रुटि (NETWORK_ERROR): कनेक्शन जांचें।"
                        GeminiErrorType.UNKNOWN_ERROR ->
                            "त्रुटि ($code - UNKNOWN_ERROR): ${errorDetails.userFriendlyMessage}"
                    }
                    Pair(false, msg)
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Validation request cancelled.")
            throw e
        } catch (e: IOException) {
            Log.d(TAG, "Network error during validation")
            Pair(false, "नेटवर्क त्रुटि (NETWORK_ERROR): इंटरनेट कनेक्शन चेक करें।")
        } catch (e: Exception) {
            Log.d(TAG, "Validation failed: ${e.localizedMessage}")
            Pair(false, "कनेक्शन त्रुटि: ${e.localizedMessage ?: "इंटरनेट कनेक्शन चेक करें"}")
        }
    }

    private fun parseErrorResponse(code: Int, responseBody: String): GeminiErrorDetails {
        var rawMessage = ""
        try {
            val json = JSONObject(responseBody)
            val errObj = json.optJSONObject("error")
            if (errObj != null) {
                rawMessage = errObj.optString("message", "")
            }
        } catch (e: Exception) {
            rawMessage = responseBody.take(100)
        }

        val type = when {
            code == 404 -> GeminiErrorType.MODEL_NOT_FOUND
            code == 429 -> GeminiErrorType.RATE_LIMITED
            code in 500..599 -> GeminiErrorType.SERVER_ERROR
            code == 400 || code == 401 || code == 403 -> {
                val lower = rawMessage.lowercase()
                if (lower.contains("api_key") || lower.contains("api key") || lower.contains("permission") || lower.contains("unauthenticated") || lower.contains("credential") || lower.contains("not valid")) {
                    GeminiErrorType.INVALID_API_KEY
                } else if (lower.contains("quota") || lower.contains("rate")) {
                    GeminiErrorType.RATE_LIMITED
                } else {
                    GeminiErrorType.INVALID_API_KEY
                }
            }
            else -> GeminiErrorType.UNKNOWN_ERROR
        }

        val userFriendly = if (rawMessage.isNotBlank()) rawMessage else "HTTP $code"
        return GeminiErrorDetails(type, code, rawMessage, userFriendly)
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
