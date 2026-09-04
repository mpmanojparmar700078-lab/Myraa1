package com.example.apis.models

import java.util.UUID

/**
 * Data model defining a public API entry in the Generic API Registry.
 * Fully configurable without hardcoded classes per endpoint.
 */
data class ApiDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val baseUrl: String,
    val endpoint: String,
    val httpMethod: String = "GET",
    val auth: String = "None",
    val https: Boolean = true,
    val cors: String = "Yes",
    val parameters: List<ApiParameter> = emptyList(),
    val responseType: String = "json",
    val responseMapping: Map<String, String> = emptyMap(),
    val documentationUrl: String = "",
    val enabled: Boolean = true,
    val timeoutMs: Long = 8000L,
    val rateLimitInfo: String = "",
    val cacheTtlSeconds: Long = 0L,
    val tags: List<String> = emptyList(),
    val capabilities: List<String> = emptyList()
)

data class ApiParameter(
    val name: String,
    val type: String = "string",
    val required: Boolean = false,
    val description: String = "",
    val defaultValue: String = "",
    val location: String = "query" // "path", "query", "header", "body"
)

data class ApiResponse(
    val success: Boolean,
    val statusCode: Int,
    val rawJson: String,
    val extractedData: Map<String, Any?> = emptyMap(),
    val formattedSummary: String = "",
    val errorMessage: String? = null,
    val isCached: Boolean = false,
    val latencyMs: Long = 0L,
    val apiId: String = "",
    val apiName: String = ""
)

data class ApiCallRecord(
    val id: String = UUID.randomUUID().toString(),
    val apiId: String,
    val apiName: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val statusCode: Int,
    val success: Boolean,
    val latencyMs: Long,
    val summary: String,
    val errorMessage: String? = null
)
