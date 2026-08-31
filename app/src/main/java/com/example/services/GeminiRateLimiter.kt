package com.example.services

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result status of the last Gemini API operation.
 */
enum class GeminiResultStatus {
    NONE,
    SUCCESS,
    RATE_LIMITED,
    COOLDOWN_ACTIVE,
    ERROR
}

/**
 * Snapshot for developer inspection UI.
 */
data class GeminiRateLimiterDebugState(
    val requestsInCurrentMinute: Int = 0,
    val maxRequestsPerMinute: Int = GeminiRateLimiter.MAX_REQUESTS_PER_MINUTE,
    val lastRequestTimestamp: Long = 0L,
    val isCooldownActive: Boolean = false,
    val cooldownRemainingSeconds: Long = 0L,
    val lastResult: GeminiResultStatus = GeminiResultStatus.NONE,
    val lastResultDetails: String = ""
) {
    val formattedLastRequestTime: String
        get() = if (lastRequestTimestamp > 0) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastRequestTimestamp))
        } else {
            "None"
        }
}

/**
 * Result of checking whether a Gemini request can proceed.
 */
sealed class RateLimitCheckResult {
    object Allowed : RateLimitCheckResult()
    data class BlockedByCooldown(val remainingSeconds: Long) : RateLimitCheckResult()
    data class BlockedByRpmLimit(val currentCount: Int, val maxCount: Int) : RateLimitCheckResult()
}

/**
 * Centralized Gemini Request & Rate-Limit Protection System.
 *
 * Implements:
 * - Single source of truth for rate limiting configurations (MAX_REQUESTS_PER_MINUTE, MIN_REQUEST_INTERVAL_MS).
 * - Client-side sliding-window RPM rate limiter (60 seconds window).
 * - Request spacing to prevent rapid-fire requests.
 * - Global persistent cooldown on HTTP 429 (NO automatic retry, NO model fallback).
 * - Safe state reporting for developer inspection (never logs or exposes API keys).
 */
object GeminiRateLimiter {
    // =========================================================================
    // CONFIGURABLE RATE LIMIT CONSTANTS (Single Central Location)
    // =========================================================================
    const val MAX_REQUESTS_PER_MINUTE = 10
    const val MIN_REQUEST_INTERVAL_MS = 1500L
    const val DEFAULT_COOLDOWN_MS = 30_000L
    private const val SLIDING_WINDOW_MS = 60_000L

    private val lock = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()

    @Volatile
    private var lastRequestTimestamp: Long = 0L

    @Volatile
    private var geminiCooldownUntil: Long = 0L

    @Volatile
    private var lastResult: GeminiResultStatus = GeminiResultStatus.NONE

    @Volatile
    private var lastResultDetails: String = ""

    private val _debugState = MutableStateFlow(GeminiRateLimiterDebugState())
    val debugState = _debugState.asStateFlow()

    /**
     * Checks if a new Gemini request is permitted.
     * Enforces active cooldowns, sliding-window RPM limits, and request spacing.
     */
    suspend fun checkAndAcquirePermission(): RateLimitCheckResult = lock.withLock {
        val now = System.currentTimeMillis()

        // 1. Check Global Cooldown
        if (now < geminiCooldownUntil) {
            val remainingSec = ((geminiCooldownUntil - now) / 1000L) + 1L
            lastResult = GeminiResultStatus.COOLDOWN_ACTIVE
            lastResultDetails = "Active cooldown ($remainingSec s remaining)"
            updateDebugState(now)
            return RateLimitCheckResult.BlockedByCooldown(remainingSec)
        }

        // 2. Clean sliding window (remove timestamps older than 60s)
        cleanOldTimestamps(now)

        // 3. Check RPM Limit
        if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
            lastResult = GeminiResultStatus.RATE_LIMITED
            lastResultDetails = "RPM limit reached (${requestTimestamps.size}/$MAX_REQUESTS_PER_MINUTE)"
            updateDebugState(now)
            return RateLimitCheckResult.BlockedByRpmLimit(requestTimestamps.size, MAX_REQUESTS_PER_MINUTE)
        }

        // 4. Request Spacing (Minimum interval between requests)
        val timeSinceLastRequest = now - lastRequestTimestamp
        if (lastRequestTimestamp > 0 && timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
            val waitTime = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest
            delay(waitTime)
        }

        // Record this request attempt
        val executeTime = System.currentTimeMillis()
        requestTimestamps.addLast(executeTime)
        lastRequestTimestamp = executeTime
        updateDebugState(executeTime)

        return RateLimitCheckResult.Allowed
    }

    /**
     * Handles HTTP 429 (Resource Exhausted / Rate Limit).
     * Strictly activates persistent cooldown. DOES NOT RETRY.
     */
    suspend fun handleHttp429(retryAfterSeconds: Long? = null): Long = lock.withLock {
        val now = System.currentTimeMillis()
        val cooldownDurationMs = when {
            retryAfterSeconds != null && retryAfterSeconds in 1..120 -> retryAfterSeconds * 1000L
            else -> DEFAULT_COOLDOWN_MS
        }

        geminiCooldownUntil = now + cooldownDurationMs
        lastResult = GeminiResultStatus.RATE_LIMITED
        lastResultDetails = "HTTP 429 received. Cooldown set for ${cooldownDurationMs / 1000}s"
        updateDebugState(now)

        return (cooldownDurationMs / 1000L)
    }

    /**
     * Records a successful Gemini API request response.
     */
    suspend fun recordSuccess() = lock.withLock {
        val now = System.currentTimeMillis()
        lastResult = GeminiResultStatus.SUCCESS
        lastResultDetails = "Request completed successfully"
        updateDebugState(now)
    }

    /**
     * Records an error response (excluding 429).
     */
    suspend fun recordError(message: String) = lock.withLock {
        val now = System.currentTimeMillis()
        lastResult = GeminiResultStatus.ERROR
        lastResultDetails = message.take(120)
        updateDebugState(now)
    }

    /**
     * Refreshes and updates the debug state snapshot.
     */
    fun refreshDebugState() {
        val now = System.currentTimeMillis()
        cleanOldTimestamps(now)
        updateDebugState(now)
    }

    private fun cleanOldTimestamps(now: Long) {
        val cutoff = now - SLIDING_WINDOW_MS
        while (requestTimestamps.isNotEmpty() && requestTimestamps.first() < cutoff) {
            requestTimestamps.removeFirst()
        }
    }

    private fun updateDebugState(now: Long) {
        val cooldownActive = now < geminiCooldownUntil
        val cooldownRemainingSec = if (cooldownActive) {
            ((geminiCooldownUntil - now) / 1000L) + 1L
        } else {
            0L
        }

        _debugState.value = GeminiRateLimiterDebugState(
            requestsInCurrentMinute = requestTimestamps.size,
            maxRequestsPerMinute = MAX_REQUESTS_PER_MINUTE,
            lastRequestTimestamp = lastRequestTimestamp,
            isCooldownActive = cooldownActive,
            cooldownRemainingSeconds = cooldownRemainingSec,
            lastResult = lastResult,
            lastResultDetails = lastResultDetails
        )
    }

    /**
     * Helper for unit tests to reset rate limiter state.
     */
    suspend fun resetForTesting() = lock.withLock {
        requestTimestamps.clear()
        lastRequestTimestamp = 0L
        geminiCooldownUntil = 0L
        lastResult = GeminiResultStatus.NONE
        lastResultDetails = ""
        updateDebugState(System.currentTimeMillis())
    }
}
