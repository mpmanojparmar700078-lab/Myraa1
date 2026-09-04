package com.example.apis.routing

import android.content.Context
import android.util.Log
import com.example.apis.client.GenericApiClient
import com.example.apis.models.ApiCallRecord
import com.example.apis.models.ApiDefinition
import com.example.apis.models.ApiResponse
import com.example.apis.registry.ApiRegistry
import java.util.Locale

/**
 * Intelligent Router for Public APIs.
 * Matches natural-language requests or AI-detected capabilities to APIs in the Registry,
 * executes them via GenericApiClient, formats natural responses, and supports fallback.
 */
class ApiToolRouter(
    private val context: Context,
    private val registry: ApiRegistry = ApiRegistry.getInstance(context),
    private val client: GenericApiClient = GenericApiClient(context)
) {
    companion object {
        private const val TAG = "ApiToolRouter"
    }

    /**
     * Executes an API request either by explicit apiId or by capability matching.
     */
    suspend fun routeAndExecute(
        apiId: String?,
        capability: String? = null,
        userQuery: String? = null,
        parameters: Map<String, String> = emptyMap()
    ): ApiResponse {
        // 1. Resolve Target API
        val targetApi: ApiDefinition? = when {
            !apiId.isNullOrBlank() -> registry.getApiById(apiId)?.takeIf { it.enabled }
            !capability.isNullOrBlank() -> registry.findApisByCapability(capability).firstOrNull { it.enabled }
            !userQuery.isNullOrBlank() -> matchApiByQuery(userQuery)
            else -> null
        }

        if (targetApi == null) {
            Log.w(TAG, "[ROUTER] No enabled API found for id=$apiId, cap=$capability, query=$userQuery")
            return ApiResponse(
                success = false,
                statusCode = 0,
                rawJson = "",
                formattedSummary = "इस विषय के लिए कोई उपयुक्त API उपलब्ध नहीं मिली। (No matching enabled API found).",
                errorMessage = "No matching enabled API found"
            )
        }

        // 2. Extract or augment parameters if needed from user query
        val mergedParams = augmentParameters(targetApi, userQuery, parameters)

        // 3. Execute Call via GenericApiClient
        var response = client.execute(targetApi, mergedParams)

        // 4. Fallback if failed and an alternative exists in the same category
        if (!response.success && targetApi.category.isNotBlank()) {
            val alternatives = registry.findApisByCategory(targetApi.category)
                .filter { it.enabled && it.id != targetApi.id }
            val fallbackApi = alternatives.firstOrNull()
            if (fallbackApi != null) {
                Log.i(TAG, "[ROUTER_FALLBACK] Primary API ${targetApi.name} failed (${response.errorMessage}). Trying fallback: ${fallbackApi.name}")
                val fallbackResponse = client.execute(fallbackApi, mergedParams)
                if (fallbackResponse.success) {
                    response = fallbackResponse
                }
            }
        }

        // 5. Record telemetry in Registry
        registry.recordApiCall(
            ApiCallRecord(
                apiId = response.apiId.ifBlank { targetApi.id },
                apiName = response.apiName.ifBlank { targetApi.name },
                category = targetApi.category,
                statusCode = response.statusCode,
                success = response.success,
                latencyMs = response.latencyMs,
                summary = response.formattedSummary.take(150),
                errorMessage = response.errorMessage
            )
        )

        return response
    }

    /**
     * Matches a natural language user query to the best suited API in the registry.
     */
    fun matchApiByQuery(query: String): ApiDefinition? {
        val lower = query.lowercase(Locale.ROOT)

        // Direct keyword matching against API tags and capabilities
        val enabledApis = registry.getAllApis().filter { it.enabled }

        // Specific high-confidence matches:
        if (lower.contains("joke") || lower.contains("chutkula") || lower.contains("चुटकुला") || lower.contains("funny") || lower.contains("laugh")) {
            return enabledApis.firstOrNull { it.id == "jokeapi" } ?: enabledApis.firstOrNull { it.id == "chucknorris" }
        }
        if (lower.contains("fruit") || lower.contains("फल") || lower.contains("nutrition") || lower.contains("calories in")) {
            return enabledApis.firstOrNull { it.id == "fruityvice" }
        }
        if (lower.contains("cat fact") || lower.contains("बिल्ली") || (lower.contains("cat") && lower.contains("fact"))) {
            return enabledApis.firstOrNull { it.id == "cat_facts" }
        }
        if (lower.contains("dog fact") || lower.contains("कुत्ता") || (lower.contains("dog") && lower.contains("fact"))) {
            return enabledApis.firstOrNull { it.id == "dog_facts" }
        }
        if (lower.contains("trivia") || lower.contains("quiz") || lower.contains("पहेली") || lower.contains("सवाल")) {
            return enabledApis.firstOrNull { it.id == "open_trivia" }
        }
        if (lower.contains("bored") || lower.contains("activity") || lower.contains("मन नहीं लग रहा") || lower.contains("क्या करूं")) {
            return enabledApis.firstOrNull { it.id == "bored_api" }
        }
        if (lower.contains("quote") || lower.contains("विचार") || lower.contains("सुविचार") || lower.contains("motivat")) {
            return enabledApis.firstOrNull { it.id == "quotable" }
        }
        if (lower.contains("game") || lower.contains("गेम") || lower.contains("free to play") || lower.contains("pc game")) {
            return enabledApis.firstOrNull { it.id == "freetogame" }
        }
        if (lower.contains("crypto") || lower.contains("bitcoin") || lower.contains("ethereum") || lower.contains("बिटकॉइन")) {
            return enabledApis.firstOrNull { it.id == "coingecko_simple" }
        }
        if (lower.contains("weather") || lower.contains("मौसम") || lower.contains("temperature") || lower.contains("तापमान")) {
            // If query mentions a specific city (e.g. "weather in tokyo"), prefer wttr_in
            return if (lower.contains("in ") || lower.contains("of ") || lower.contains("का मौसम")) {
                enabledApis.firstOrNull { it.id == "wttr_in" } ?: enabledApis.firstOrNull { it.id == "open_meteo" }
            } else {
                enabledApis.firstOrNull { it.id == "open_meteo" } ?: enabledApis.firstOrNull { it.id == "wttr_in" }
            }
        }
        if (lower.contains("country") || lower.contains("देश") || lower.contains("capital of") || lower.contains("राजधानी")) {
            return enabledApis.firstOrNull { it.id == "rest_countries" }
        }
        if (lower.contains("spacex") || lower.contains("space launch") || lower.contains("rocket")) {
            return enabledApis.firstOrNull { it.id == "spacex" }
        }
        if (lower.contains("dictionary") || lower.contains("meaning of") || lower.contains("define ") || lower.contains("का अर्थ")) {
            return enabledApis.firstOrNull { it.id == "dictionary" }
        }
        if (lower.contains("advice") || lower.contains("सलाह") || lower.contains("सुझाव")) {
            return enabledApis.firstOrNull { it.id == "adviceslip" }
        }
        if (lower.contains("number fact") || lower.contains("math fact")) {
            return enabledApis.firstOrNull { it.id == "numbersapi" }
        }
        if (lower.contains("university") || lower.contains("college") || lower.contains("कॉलेज") || lower.contains("विश्वविद्यालय")) {
            return enabledApis.firstOrNull { it.id == "universities" }
        }
        if (lower.contains("sunrise") || lower.contains("sunset") || lower.contains("सूर्योदय") || lower.contains("सूर्यास्त")) {
            return enabledApis.firstOrNull { it.id == "sunrise_sunset" }
        }

        // Generic keyword scoring
        var bestScore = 0
        var bestApi: ApiDefinition? = null

        for (api in enabledApis) {
            var score = 0
            for (tag in api.tags) {
                if (lower.contains(tag.lowercase(Locale.ROOT))) score += 2
            }
            for (cap in api.capabilities) {
                if (lower.contains(cap.lowercase(Locale.ROOT))) score += 3
            }
            if (score > bestScore) {
                bestScore = score
                bestApi = api
            }
        }

        return if (bestScore > 0) bestApi else null
    }

    private fun augmentParameters(
        api: ApiDefinition,
        query: String?,
        existing: Map<String, String>
    ): Map<String, String> {
        val result = existing.toMutableMap()
        if (query.isNullOrBlank()) return result

        val lower = query.lowercase(Locale.ROOT)

        when (api.id) {
            "fruityvice" -> {
                if (!result.containsKey("name")) {
                    val commonFruits = listOf("apple", "banana", "mango", "orange", "strawberry", "pineapple", "watermelon", "grape", "lemon", "peach", "pear", "cherry", "kiwi", "blueberry", "papaya", "guava")
                    val found = commonFruits.firstOrNull { lower.contains(it) }
                    result["name"] = found ?: "apple"
                }
            }

            "wttr_in" -> {
                if (!result.containsKey("location")) {
                    // Extract city name e.g. "weather in Tokyo" -> "Tokyo"
                    val patterns = listOf(
                        "weather in ([a-zA-Z]+)".toRegex(),
                        "weather of ([a-zA-Z]+)".toRegex(),
                        "([a-zA-Z]+) ka mausam".toRegex(),
                        "([a-zA-Z]+) weather".toRegex()
                    )
                    for (pattern in patterns) {
                        val match = pattern.find(lower)
                        if (match != null && match.groupValues.size > 1) {
                            result["location"] = match.groupValues[1].replaceFirstChar { it.uppercase() }
                            break
                        }
                    }
                    if (!result.containsKey("location")) {
                        result["location"] = "Delhi"
                    }
                }
            }

            "rest_countries" -> {
                if (!result.containsKey("name")) {
                    val commonCountries = listOf("india", "japan", "germany", "france", "canada", "brazil", "australia", "china", "russia", "usa", "united states", "uk", "italy", "spain")
                    val found = commonCountries.firstOrNull { lower.contains(it) }
                    result["name"] = found ?: "india"
                }
            }

            "dictionary" -> {
                if (!result.containsKey("word")) {
                    val definePatterns = listOf(
                        "meaning of ([a-zA-Z]+)".toRegex(),
                        "define ([a-zA-Z]+)".toRegex(),
                        "what does ([a-zA-Z]+) mean".toRegex(),
                        "definition of ([a-zA-Z]+)".toRegex()
                    )
                    for (p in definePatterns) {
                        val match = p.find(lower)
                        if (match != null && match.groupValues.size > 1) {
                            result["word"] = match.groupValues[1]
                            break
                        }
                    }
                    if (!result.containsKey("word")) {
                        result["word"] = "serendipity"
                    }
                }
            }

            "universities" -> {
                if (!result.containsKey("name")) {
                    val uniPatterns = listOf(
                        "university in ([a-zA-Z]+)".toRegex(),
                        "([a-zA-Z]+) university".toRegex()
                    )
                    for (p in uniPatterns) {
                        val match = p.find(lower)
                        if (match != null && match.groupValues.size > 1) {
                            result["name"] = match.groupValues[1]
                            break
                        }
                    }
                    if (!result.containsKey("name")) {
                        result["name"] = "Delhi"
                    }
                }
            }
        }

        return result
    }
}
