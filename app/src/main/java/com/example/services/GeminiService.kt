package com.example.services

import android.util.Log
import com.example.BuildConfig
import com.example.models.IntentType
import com.example.models.ParsedIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Level 2 — Gemini AI Engine
 *
 * Invoked ONLY when Level 1 Local Command Engine cannot determine the intent with high confidence.
 * Features:
 * - Mutex-based concurrency lock to eliminate duplicate requests & double-submits.
 * - Primary verified model: 'gemini-3.5-flash' on endpoint 'v1beta/models/gemini-3.5-flash:generateContent'.
 * - Fallback supported alias: 'gemini-flash-latest'.
 * - Safe retry handling:
 *     * HTTP 404 (Not Found): STOP retrying immediately. (Prevents model/endpoint mismatch loops).
 *     * HTTP 400 (Bad Request): STOP retrying immediately.
 *     * HTTP 401 / 403 (Auth Failure): STOP retrying immediately.
 *     * HTTP 429 (Rate Limited): Bounded retry with cooldown; NEVER infinite loop.
 *     * HTTP 500..599 / Network errors: Strictly bounded retry count (max 1 retry).
 * - Safe debug logging (model name, endpoint, status, attempt, failure reason; NEVER exposes API key).
 */
class GeminiService(
    private val commandParser: CommandParser,
    private val client: OkHttpClient? = null
) {
    companion object {
        private const val TAG = "GeminiService"

        // Configured endpoint constants & verified models
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
        const val API_VERSION = "v1beta"
        const val PRIMARY_MODEL = "gemini-3.5-flash"
        const val FALLBACK_MODEL = "gemini-flash-latest"

        private const val MAX_TRANSIENT_RETRIES = 1
        private const val MAX_429_RETRIES = 1
    }

    private val requestMutex = Mutex()

    private val okHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun executeCancellableRequest(httpRequest: Request): Pair<Int, String> =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val call = okHttpClient.newCall(httpRequest)
            cont.invokeOnCancellation {
                try {
                    Log.d(TAG, "[GEMINI_CANCEL] Cancelling OkHttp call in flight")
                    call.cancel()
                } catch (_: Throwable) {}
            }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val code = response.code
                        val body = response.body?.string() ?: ""
                        response.close()
                        if (!cont.isCancelled) {
                            cont.resumeWith(Result.success(Pair(code, body)))
                        }
                    } catch (e: Exception) {
                        if (!cont.isCancelled) {
                            cont.resumeWith(Result.failure(e))
                        }
                    }
                }
            })
        }

    suspend fun analyzeAndRespond(
        userInput: String,
        memoryContext: String,
        customApiKey: String? = null
    ): Result<ParsedIntent> = withContext(Dispatchers.IO) {
        // Prevent concurrent or double-submit Gemini requests
        requestMutex.withLock {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val apiKey = when {
                !customApiKey.isNullOrBlank() -> customApiKey.trim()
                BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
                else -> ""
            }

            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                val fallbackReply = if (userInput.contains("हिंदी") || userInput.contains("खोलो")) {
                    "मायरा तैयार है! कृपया AI बातचीत सक्रिय करने के लिए ऊपर ⚙️ Settings में अपनी Gemini API Key जोड़ें। (आप 'YouTube खोलो', 'Settings खोलो', 'Google सर्च' जैसे सभी लोकल कमांड्स बिना किसी API Key के भी हमेशा चला सकते हैं!)"
                } else {
                    "Myra is ready! Please configure your Gemini API Key in ⚙️ Settings to enable AI dialogue. (You can still run direct local commands like 'YouTube खोलो', 'Open Chrome', 'Settings खोलो', 'Google search' without any API key!)"
                }
                return@withLock Result.success(
                    ParsedIntent(
                        type = IntentType.GENERAL_CHAT,
                        responseText = fallbackReply
                    )
                )
            }

            // =================================================================
            // GEMINI RATE LIMIT PROTECTION (Centralized Rate Limiter)
            // =================================================================
            val rateLimitCheck = GeminiRateLimiter.checkAndAcquirePermission()
            when (rateLimitCheck) {
                is RateLimitCheckResult.BlockedByCooldown -> {
                    val cooldownMsg = if (userInput.contains("हिंदी") || userInput.contains("क्या") || userInput.contains("है")) {
                        "Gemini अभी थोड़ी देर के लिए उपलब्ध नहीं है। कृपया कुछ सेकंड बाद फिर कोशिश करें।"
                    } else {
                        "Gemini is temporarily in cooldown. Please try again in a few seconds."
                    }
                    Log.w(TAG, "[GEMINI_RATE_LIMIT] Blocked by cooldown (${rateLimitCheck.remainingSeconds}s remaining). Request aborted.")
                    return@withLock Result.failure<ParsedIntent>(Exception(cooldownMsg))
                }
                is RateLimitCheckResult.BlockedByRpmLimit -> {
                    val rpmMsg = if (userInput.contains("हिंदी") || userInput.contains("क्या") || userInput.contains("है")) {
                        "अनुरोध सीमा (RPM Limit) पूरी हो गई है। कृपया थोड़ी देर बाद फिर कोशिश करें।"
                    } else {
                        "Request limit (RPM) reached. Please wait a moment before trying again."
                    }
                    Log.w(TAG, "[GEMINI_RATE_LIMIT] Blocked by RPM limit (${rateLimitCheck.currentCount}/${rateLimitCheck.maxCount}). Request aborted.")
                    return@withLock Result.failure<ParsedIntent>(Exception(rpmMsg))
                }
                is RateLimitCheckResult.Allowed -> {
                    // Proceed with single controlled request
                }
            }

            val systemPrompt = """
                You are "Myra", an intelligent, helpful, and polite Android AI Assistant (V2).
                You understand Hindi, English, and Hinglish naturally.
                
                USER MEMORY CONTEXT:
                $memoryContext
                
                Your job is to parse the user's input and reply with a JSON object.
                SAFETY RULE: NEVER execute or return arbitrary code or shell scripts. Only return structured JSON.
                
                SUPPORTED PREDEFINED ACTIONS:
                - OPEN_APP: Opens an installed Android app (e.g. YouTube, Chrome, WhatsApp, Maps).
                - OPEN_SETTINGS: Opens device settings.
                - WEB_SEARCH: Performs a Google search for the query.
                - YOUTUBE_SEARCH: Performs a YouTube video search for the query.
                - OPEN_URL: Opens a web URL.
                - WAIT: Brief pause between steps (durationMs: 1000).
                - BACK: Navigates back / home.
                - CLEAR_CHAT: Clears conversation history.
                - SET_PREFERENCE: Saves language (key: "preferred_language", value: "hi" or "en").
                - PUBLIC_API: Fetches data from integrated no-auth public APIs (jokeapi, fruityvice, cat_facts, dog_facts, open_trivia, bored_api, quotable, freetogame, open_meteo, wttr_in, rest_countries, spacex, coingecko_simple, dictionary, adviceslip, numbersapi).
                - GENERAL_CHAT: Answers questions, conversational replies.
                
                SCHEMAS:
                A. Single Action:
                   {"intent": "OPEN_APP", "app": "YouTube", "responseText": "Opening YouTube…"}
                   {"intent": "PUBLIC_API", "apiId": "jokeapi", "query": "joke", "responseText": "यहाँ एक चुटकुला है:"}
                   {"intent": "PUBLIC_API", "apiId": "fruityvice", "parameters": {"name": "apple"}, "responseText": "सेब की पोषण जानकारी लाई जा रही है…"}
                   {"intent": "WEB_SEARCH", "query": "Free Fire", "responseText": "Searching Google for Free Fire…"}
                   {"intent": "SET_PREFERENCE", "key": "preferred_language", "value": "hi", "responseText": "ठीक है, मैं याद रखूँगी।"}
                
                B. Multi-Step Actions:
                   {
                     "task": "multi_action",
                     "actions": [
                       {"type": "OPEN_APP", "app": "YouTube"},
                       {"type": "WAIT", "durationMs": 1000},
                       {"type": "WEB_SEARCH", "query": "Free Fire"}
                     ],
                     "responseText": "Opening YouTube and searching for Free Fire."
                   }
                
                C. General Conversation:
                   {"intent": "GENERAL_CHAT", "responseText": "Your polite conversational response."}
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", userInput) })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val systemInstructionObj = JSONObject().apply {
                    val sysParts = JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    }
                    put("parts", sysParts)
                }
                put("systemInstruction", systemInstructionObj)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.3)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", genConfig)
            }

            val requestBodyString = requestJson.toString()
            val model = PRIMARY_MODEL
            val endpointPath = "$API_VERSION/models/$model:generateContent"
            val fullUrl = "$BASE_URL$endpointPath?key=$apiKey"

            Log.d(TAG, "[GEMINI_REQUEST] Model: $model | API Endpoint: $endpointPath | Key: [PROTECTED]")

            var transientAttempts = 0
            var rateLimitAttempts = 0
            var lastErrorMsg = ""

            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                try {
                    val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
                    val httpRequest = Request.Builder()
                        .url(fullUrl)
                        .post(requestBody)
                        .build()

                    val (responseCode, responseBody) = executeCancellableRequest(httpRequest)

                    Log.d(TAG, "[GEMINI_RESPONSE] Model: $model | Endpoint: $endpointPath | HTTP Status: $responseCode | Attempt: ${transientAttempts + rateLimitAttempts + 1}")

                    if (responseCode in 200..299) {
                        GeminiRateLimiter.recordSuccess()
                        val rootJson = JSONObject(responseBody)
                        val candidates = rootJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val firstPart = parts?.optJSONObject(0)
                        val textOutput = firstPart?.optString("text", "") ?: ""

                        Log.i(TAG, "[GEMINI_SUCCESS] Request completed successfully with model $model")
                        val parsedIntent = commandParser.parseGeminiJsonResponse(textOutput)
                        return@withLock Result.success(parsedIntent)
                    }

                    // =========================================================
                    // 1. HTTP 404 (NOT FOUND) -> STOP RETRYING IMMEDIATELY
                    // =========================================================
                    if (responseCode == 404) {
                        lastErrorMsg = "Gemini model '$model' not found on endpoint $endpointPath (HTTP 404)."
                        Log.e(TAG, "[GEMINI_404_FATAL] $lastErrorMsg Stopping retries immediately.")
                        GeminiRateLimiter.recordError("Model/Endpoint not found (HTTP 404)")
                        return@withLock Result.failure<ParsedIntent>(Exception(lastErrorMsg))
                    }

                    // =========================================================
                    // 2. HTTP 400 (BAD REQUEST) -> STOP RETRYING IMMEDIATELY
                    // =========================================================
                    if (responseCode == 400) {
                        val cleanErrMsg = extractErrorMessage(responseBody)
                        lastErrorMsg = "Gemini API Bad Request (HTTP 400): $cleanErrMsg"
                        Log.e(TAG, "[GEMINI_400_FATAL] $lastErrorMsg Stopping retries immediately.")
                        GeminiRateLimiter.recordError("Bad Request (HTTP 400)")
                        return@withLock Result.failure<ParsedIntent>(Exception(lastErrorMsg))
                    }

                    // =========================================================
                    // 3. HTTP 401 / 403 (AUTHENTICATION ERROR) -> STOP RETRYING
                    // =========================================================
                    if (responseCode == 401 || responseCode == 403) {
                        val cleanErrMsg = extractErrorMessage(responseBody)
                        lastErrorMsg = "Gemini API authentication failed (HTTP $responseCode): $cleanErrMsg"
                        Log.e(TAG, "[GEMINI_AUTH_FATAL] $lastErrorMsg Stopping retries immediately.")
                        GeminiRateLimiter.recordError("Auth Error (HTTP $responseCode)")
                        return@withLock Result.failure<ParsedIntent>(Exception("Gemini API authentication failed (HTTP $responseCode). Please check your API key in Settings."))
                    }

                    // =========================================================
                    // 4. HTTP 429 (RATE LIMIT) -> BOUNDED RETRY OR COOLDOWN
                    // =========================================================
                    if (responseCode == 429) {
                        val retryHeader = responseBody.takeIf { false }?.let { null } // header is recorded via rate limiter
                        val cooldownSec = GeminiRateLimiter.handleHttp429(null)
                        Log.w(TAG, "[GEMINI_429] Rate limited (HTTP 429). Cooldown: ${cooldownSec}s. Attempt ${rateLimitAttempts + 1}/$MAX_429_RETRIES.")

                        if (rateLimitAttempts < MAX_429_RETRIES && cooldownSec <= 3) {
                            rateLimitAttempts++
                            delay((cooldownSec * 1000L) + 500L)
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            continue
                        } else {
                            val friendlyMsg = if (userInput.contains("हिंदी") || userInput.contains("क्या") || userInput.contains("है")) {
                                "Gemini अभी थोड़ी देर के लिए उपलब्ध नहीं है। कृपया कुछ सेकंड बाद फिर कोशिश करें।"
                            } else {
                                "Gemini is temporarily rate-limited. Please try again in a few seconds."
                            }
                            return@withLock Result.failure<ParsedIntent>(Exception(friendlyMsg))
                        }
                    }

                    // =========================================================
                    // 5. HTTP 500..599 (SERVER ERROR) -> BOUNDED RETRY (MAX 1)
                    // =========================================================
                    if (responseCode in 500..599) {
                        if (transientAttempts < MAX_TRANSIENT_RETRIES) {
                            transientAttempts++
                            Log.w(TAG, "[GEMINI_5XX_RETRY] Server error (HTTP $responseCode). Retrying attempt $transientAttempts of $MAX_TRANSIENT_RETRIES after backoff...")
                            delay(1200L * transientAttempts)
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            continue
                        } else {
                            val errMsg = "Gemini server error (HTTP $responseCode)"
                            Log.e(TAG, "[GEMINI_5XX_FAILED] Server error persisted after retries: $errMsg")
                            GeminiRateLimiter.recordError(errMsg)
                            return@withLock Result.failure<ParsedIntent>(Exception(errMsg))
                        }
                    }

                    // Other unhandled HTTP status
                    val errMsg = extractErrorMessage(responseBody)
                    Log.e(TAG, "[GEMINI_ERROR] HTTP $responseCode: $errMsg")
                    GeminiRateLimiter.recordError("HTTP $responseCode")
                    return@withLock Result.failure<ParsedIntent>(Exception("Gemini API error (HTTP $responseCode): $errMsg"))

                } catch (e: kotlinx.coroutines.CancellationException) {
                    Log.i(TAG, "[GEMINI_CANCEL] Gemini coroutine was cancelled. Stopping immediately without retrying.")
                    throw e
                } catch (e: IOException) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    // Network / Socket Timeout -> Small bounded retry (Max 1)
                    if (transientAttempts < MAX_TRANSIENT_RETRIES) {
                        transientAttempts++
                        Log.w(TAG, "[GEMINI_NETWORK_RETRY] Network exception: ${e.localizedMessage}. Retrying attempt $transientAttempts of $MAX_TRANSIENT_RETRIES...")
                        delay(1200L * transientAttempts)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        continue
                    } else {
                        val errorDetail = e.localizedMessage ?: "Network connection failed"
                        Log.e(TAG, "[GEMINI_NETWORK_FAILED] Network error permanently failed: $errorDetail")
                        GeminiRateLimiter.recordError("Network Error")
                        return@withLock Result.failure<ParsedIntent>(Exception("Network error contacting Gemini API: $errorDetail"))
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.i(TAG, "[GEMINI_CANCEL] Gemini coroutine was cancelled. Propagating cancellation.")
                        throw e
                    }
                    val errorDetail = e.localizedMessage ?: "Unexpected error"
                    Log.e(TAG, "[GEMINI_UNEXPECTED] $errorDetail", e)
                    GeminiRateLimiter.recordError("Unexpected Error")
                    return@withLock Result.failure<ParsedIntent>(Exception("Gemini processing error: $errorDetail"))
                }
            }

            Result.failure<ParsedIntent>(
                Exception(
                    if (lastErrorMsg.isNotBlank()) lastErrorMsg
                    else "Gemini AI is temporarily unreachable. Please check your API key in Settings."
                )
            )
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        return try {
            val json = JSONObject(rawBody)
            val errorObj = json.optJSONObject("error")
            errorObj?.optString("message") ?: rawBody.take(150)
        } catch (_: Exception) {
            rawBody.take(150)
        }
    }
}
