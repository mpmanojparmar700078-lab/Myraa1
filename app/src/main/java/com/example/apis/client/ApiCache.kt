package com.example.apis.client

import com.example.apis.models.ApiResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache with TTL for static/non-realtime API results.
 */
class ApiCache {

    private data class CacheEntry(
        val response: ApiResponse,
        val expiryTimestamp: Long
    )

    private val cacheMap = ConcurrentHashMap<String, CacheEntry>()

    fun get(cacheKey: String): ApiResponse? {
        val entry = cacheMap[cacheKey] ?: return null
        if (System.currentTimeMillis() > entry.expiryTimestamp) {
            cacheMap.remove(cacheKey)
            return null
        }
        return entry.response.copy(isCached = true)
    }

    fun put(cacheKey: String, response: ApiResponse, ttlSeconds: Long) {
        if (ttlSeconds <= 0) return
        val expiry = System.currentTimeMillis() + (ttlSeconds * 1000L)
        cacheMap[cacheKey] = CacheEntry(response, expiry)
    }

    fun clear() {
        cacheMap.clear()
    }

    fun evict(cacheKey: String) {
        cacheMap.remove(cacheKey)
    }

    fun size(): Int = cacheMap.size
}
