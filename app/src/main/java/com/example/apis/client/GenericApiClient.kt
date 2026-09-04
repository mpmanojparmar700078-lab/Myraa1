package com.example.apis.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.apis.models.ApiDefinition
import com.example.apis.models.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Reusable, robust Generic API Client executing requests for any configured ApiDefinition.
 *
 * Features:
 * - Single shared OkHttpClient instance.
 * - HTTP status code classification (2xx, 404, 429, 5xx).
 * - Rate limit detection (HTTP 429 + Retry-After support).
 * - Bounded retry on transient 5xx server issues.
 * - Network failure & offline detection.
 * - In-memory TTL caching for non-realtime datasets.
 * - Coroutine cancellation support.
 * - Clean structured debug telemetry.
 */
class GenericApiClient(
    private val context: Context,
    private val cache: ApiCache = ApiCache(),
    private val requestBuilder: ApiRequestBuilder = ApiRequestBuilder(),
    private val responseParser: ApiResponseParser = ApiResponseParser(),
    client: OkHttpClient? = null
) {
    companion object {
        private const val TAG = "GenericApiClient"
        private const val DEFAULT_CONNECT_TIMEOUT_SEC = 10L
        private const val DEFAULT_READ_TIMEOUT_SEC = 12L
    }

    private val sharedClient: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun execute(
        api: ApiDefinition,
        parameters: Map<String, String> = emptyMap(),
        bodyContent: String? = null,
        forceRefresh: Boolean = false
    ): ApiResponse = withContext(Dispatchers.IO) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()

        // 1. Check Offline State
        if (!isNetworkAvailable()) {
            Log.w(TAG, "[API_OFFLINE] Device is offline. Cannot execute ${api.name}")
            return@withContext ApiResponse(
                success = false,
                statusCode = 0,
                rawJson = "",
                formattedSummary = "इंटरनेट कनेक्शन उपलब्ध नहीं है (Device is offline).",
                errorMessage = "Network is offline",
                apiId = api.id,
                apiName = api.name
            )
        }

        // 2. Check Cache
        val cacheKey = buildCacheKey(api.id, parameters)
        if (!forceRefresh && api.cacheTtlSeconds > 0) {
            val cached = cache.get(cacheKey)
            if (cached != null) {
                Log.d(TAG, "[API_CACHE_HIT] Served ${api.name} from cache (TTL: ${api.cacheTtlSeconds}s)")
                return@withContext cached
            }
        }

        // 3. Build HTTP Request
        val request = try {
            requestBuilder.buildRequest(api, parameters, bodyContent)
        } catch (e: Exception) {
            Log.e(TAG, "[API_BUILD_ERROR] Failed to build request for ${api.name}: ${e.localizedMessage}")
            return@withContext ApiResponse(
                success = false,
                statusCode = 0,
                rawJson = "",
                formattedSummary = "अनुरोध तैयार करने में त्रुटि (Request build error).",
                errorMessage = e.localizedMessage,
                apiId = api.id,
                apiName = api.name
            )
        }

        Log.d(TAG, "[API_REQUEST] Executing ${api.name} -> ${request.method} ${request.url}")

        var attempts = 0
        val maxAttempts = 2
        var lastStatusCode = 0
        var lastResponseBody = ""
        var lastError: Exception? = null
        val startTime = System.currentTimeMillis()

        while (attempts < maxAttempts) {
            attempts++
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            try {
                val (code, body, retryAfterSec) = executeCancellableCall(request)
                lastStatusCode = code
                lastResponseBody = body

                val latencyMs = System.currentTimeMillis() - startTime
                Log.d(TAG, "[API_RESPONSE] ${api.name} finished with HTTP $code in ${latencyMs}ms (attempt $attempts)")

                // Case: 2xx Success
                if (code in 200..299) {
                    val parsed = responseParser.parse(api, code, body, latencyMs)
                    if (api.cacheTtlSeconds > 0) {
                        cache.put(cacheKey, parsed, api.cacheTtlSeconds)
                    }
                    return@withContext parsed
                }

                // Case: 429 Rate Limit
                if (code == 429) {
                    val waitTime = (retryAfterSec ?: 2).coerceAtMost(5)
                    Log.w(TAG, "[API_429_RATE_LIMIT] ${api.name} returned 429. Cooldown: ${waitTime}s")
                    if (attempts < maxAttempts) {
                        delay(waitTime * 1000L)
                        continue
                    } else {
                        return@withContext ApiResponse(
                            success = false,
                            statusCode = 429,
                            rawJson = body,
                            formattedSummary = "${api.name} की अनुरोध सीमा समाप्त हो गई है (Rate limited). कृपया थोड़ी देर बाद प्रयास करें।",
                            errorMessage = "Rate limit exceeded (HTTP 429)",
                            apiId = api.id,
                            apiName = api.name,
                            latencyMs = latencyMs
                        )
                    }
                }

                // Case: 404 Not Found
                if (code == 404) {
                    Log.w(TAG, "[API_404] ${api.name} endpoint or resource not found (HTTP 404)")
                    return@withContext ApiResponse(
                        success = false,
                        statusCode = 404,
                        rawJson = body,
                        formattedSummary = "मांगी गई जानकारी नहीं मिली (404 Not Found for ${api.name}).",
                        errorMessage = "Resource not found (HTTP 404)",
                        apiId = api.id,
                        apiName = api.name,
                        latencyMs = latencyMs
                    )
                }

                // Case: 5xx Server Error -> bounded retry
                if (code in 500..599 && attempts < maxAttempts) {
                    Log.w(TAG, "[API_5XX_RETRY] ${api.name} server error (HTTP $code). Retrying...")
                    delay(1000L)
                    continue
                }

                // Other non-2xx codes
                val parsed = responseParser.parse(api, code, body, latencyMs)
                return@withContext parsed.copy(
                    success = false,
                    errorMessage = "HTTP $code: ${body.take(150)}"
                )

            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "[API_CANCEL] Request for ${api.name} cancelled by coroutine.")
                throw e
            } catch (e: IOException) {
                lastError = e
                Log.w(TAG, "[API_IO_EXCEPTION] ${api.name} network exception: ${e.localizedMessage}")
                if (attempts < maxAttempts) {
                    delay(800L)
                    continue
                }
            } catch (e: Exception) {
                lastError = e
                break
            }
        }

        val latencyMs = System.currentTimeMillis() - startTime
        val friendlyMessage = if (lastError is java.net.SocketTimeoutException) {
            "${api.name} से संपर्क करने में समय अधिक लग गया (Request timed out)."
        } else {
            "${api.name} अभी उपलब्ध नहीं हो सका (Service unreachable)."
        }

        ApiResponse(
            success = false,
            statusCode = lastStatusCode,
            rawJson = lastResponseBody,
            formattedSummary = friendlyMessage,
            errorMessage = lastError?.localizedMessage ?: "HTTP $lastStatusCode",
            apiId = api.id,
            apiName = api.name,
            latencyMs = latencyMs
        )
    }

    private suspend fun executeCancellableCall(request: Request): Triple<Int, String, Int?> =
        suspendCancellableCoroutine { continuation ->
            val call: Call = sharedClient.newCall(request)

            continuation.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Throwable) {}
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val code = response.code
                        val body = response.body?.string() ?: ""
                        val retryAfterHeader = response.header("Retry-After")?.toIntOrNull()
                        response.close()

                        if (!continuation.isCancelled) {
                            continuation.resume(Triple(code, body, retryAfterHeader))
                        }
                    } catch (e: Exception) {
                        if (!continuation.isCancelled) {
                            continuation.resumeWith(Result.failure(e))
                        }
                    }
                }
            })
        }

    private fun buildCacheKey(apiId: String, parameters: Map<String, String>): String {
        if (parameters.isEmpty()) return apiId
        val sortedParams = parameters.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        return "$apiId?$sortedParams"
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true // assume available if permission query fails
        }
    }
}
