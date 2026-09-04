package com.example.apis.registry

import android.content.Context
import android.util.Log
import com.example.apis.models.ApiCallRecord
import com.example.apis.models.ApiDefinition
import com.example.apis.models.ApiParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Central API Registry & Catalog.
 * Loads and manages public API definitions from local JSON (and supports remote/dynamic updates).
 */
class ApiRegistry(private val context: Context) {

    companion object {
        private const val TAG = "ApiRegistry"
        private const val DEFAULT_ASSET_PATH = "apis/public_apis.json"

        @Volatile
        private var instance: ApiRegistry? = null

        fun getInstance(context: Context): ApiRegistry {
            return instance ?: synchronized(this) {
                instance ?: ApiRegistry(context.applicationContext).also {
                    it.loadCatalogFromAssets()
                    instance = it
                }
            }
        }
    }

    private val apiMap = ConcurrentHashMap<String, ApiDefinition>()
    private val disabledApiIds = ConcurrentHashMap.newKeySet<String>()

    private val _apisFlow = MutableStateFlow<List<ApiDefinition>>(emptyList())
    val apisFlow: StateFlow<List<ApiDefinition>> = _apisFlow.asStateFlow()

    private val _callHistoryFlow = MutableStateFlow<List<ApiCallRecord>>(emptyList())
    val callHistoryFlow: StateFlow<List<ApiCallRecord>> = _callHistoryFlow.asStateFlow()

    fun loadCatalogFromAssets(assetPath: String = DEFAULT_ASSET_PATH) {
        try {
            val jsonText = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            loadCatalogFromJsonString(jsonText)
            Log.i(TAG, "[API_REGISTRY] Successfully loaded ${apiMap.size} public APIs from $assetPath")
        } catch (e: Exception) {
            Log.e(TAG, "[API_REGISTRY_ERROR] Failed to load catalog from assets: ${e.localizedMessage}")
        }
    }

    fun loadCatalogFromJsonString(jsonString: String): Int {
        try {
            val jsonArray = JSONArray(jsonString)
            val parsedList = mutableListOf<ApiDefinition>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val api = parseApiDefinition(obj)
                if (api != null) {
                    parsedList.add(api)
                    apiMap[api.id] = api
                }
            }

            updateFlow()
            return parsedList.size
        } catch (e: Exception) {
            Log.e(TAG, "[API_REGISTRY_ERROR] Failed to parse JSON catalog: ${e.localizedMessage}")
            return 0
        }
    }

    private fun parseApiDefinition(obj: JSONObject): ApiDefinition? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = obj.optString("name", id)
        val description = obj.optString("description", "")
        val category = obj.optString("category", "General")
        val baseUrl = obj.optString("baseUrl").takeIf { it.isNotBlank() } ?: return null
        val endpoint = obj.optString("endpoint", "/")
        val httpMethod = obj.optString("httpMethod", "GET")
        val auth = obj.optString("auth", "None")
        val https = obj.optBoolean("https", true)
        val cors = obj.optString("cors", "Yes")
        val responseType = obj.optString("responseType", "json")
        val docUrl = obj.optString("documentationUrl", "")
        val timeoutMs = obj.optLong("timeoutMs", 8000L)
        val rateLimitInfo = obj.optString("rateLimitInfo", "")
        val cacheTtlSeconds = obj.optLong("cacheTtlSeconds", 0L)

        // Parameters
        val paramsList = mutableListOf<ApiParameter>()
        val paramsArray = obj.optJSONArray("parameters")
        if (paramsArray != null) {
            for (j in 0 until paramsArray.length()) {
                val pObj = paramsArray.optJSONObject(j) ?: continue
                val pName = pObj.optString("name").takeIf { it.isNotBlank() } ?: continue
                paramsList.add(
                    ApiParameter(
                        name = pName,
                        type = pObj.optString("type", "string"),
                        required = pObj.optBoolean("required", false),
                        description = pObj.optString("description", ""),
                        defaultValue = pObj.optString("defaultValue", ""),
                        location = pObj.optString("location", "query")
                    )
                )
            }
        }

        // Response mapping
        val mapping = mutableMapOf<String, String>()
        val mappingObj = obj.optJSONObject("responseMapping")
        if (mappingObj != null) {
            val keys = mappingObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                mapping[k] = mappingObj.optString(k)
            }
        }

        // Tags
        val tagsList = mutableListOf<String>()
        val tagsArray = obj.optJSONArray("tags")
        if (tagsArray != null) {
            for (j in 0 until tagsArray.length()) {
                val tag = tagsArray.optString(j)
                if (tag.isNotBlank()) tagsList.add(tag)
            }
        }

        // Capabilities
        val capsList = mutableListOf<String>()
        val capsArray = obj.optJSONArray("capabilities")
        if (capsArray != null) {
            for (j in 0 until capsArray.length()) {
                val cap = capsArray.optString(j)
                if (cap.isNotBlank()) capsList.add(cap)
            }
        }

        val initiallyEnabled = obj.optBoolean("enabled", true)
        val isEnabled = if (disabledApiIds.contains(id)) false else initiallyEnabled

        return ApiDefinition(
            id = id,
            name = name,
            description = description,
            category = category,
            baseUrl = baseUrl,
            endpoint = endpoint,
            httpMethod = httpMethod,
            auth = auth,
            https = https,
            cors = cors,
            parameters = paramsList,
            responseType = responseType,
            responseMapping = mapping,
            documentationUrl = docUrl,
            enabled = isEnabled,
            timeoutMs = timeoutMs,
            rateLimitInfo = rateLimitInfo,
            cacheTtlSeconds = cacheTtlSeconds,
            tags = tagsList,
            capabilities = capsList
        )
    }

    fun getAllApis(): List<ApiDefinition> {
        return apiMap.values.toList()
    }

    fun getApiById(id: String): ApiDefinition? {
        val api = apiMap[id] ?: return null
        return api.copy(enabled = !disabledApiIds.contains(id))
    }

    fun findApisByCategory(category: String): List<ApiDefinition> {
        return getAllApis().filter {
            it.category.equals(category, ignoreCase = true)
        }
    }

    fun findApisByKeyword(keyword: String): List<ApiDefinition> {
        val lower = keyword.lowercase(Locale.ROOT).trim()
        if (lower.isBlank()) return getAllApis()

        return getAllApis().filter { api ->
            api.name.lowercase(Locale.ROOT).contains(lower) ||
            api.description.lowercase(Locale.ROOT).contains(lower) ||
            api.category.lowercase(Locale.ROOT).contains(lower) ||
            api.tags.any { it.lowercase(Locale.ROOT).contains(lower) } ||
            api.capabilities.any { it.lowercase(Locale.ROOT).contains(lower) }
        }
    }

    fun findApisByCapability(capability: String): List<ApiDefinition> {
        val lower = capability.lowercase(Locale.ROOT).trim()
        return getAllApis().filter { api ->
            api.enabled && api.capabilities.any { it.lowercase(Locale.ROOT) == lower }
        }
    }

    fun findNoAuthApis(): List<ApiDefinition> {
        return getAllApis().filter {
            it.auth.equals("none", ignoreCase = true) || it.auth.equals("no", ignoreCase = true)
        }
    }

    fun findHttpsApis(): List<ApiDefinition> {
        return getAllApis().filter { it.https }
    }

    fun getAllCategories(): List<String> {
        return apiMap.values.map { it.category }.distinct().sorted()
    }

    fun setApiEnabled(id: String, enabled: Boolean) {
        if (enabled) {
            disabledApiIds.remove(id)
        } else {
            disabledApiIds.add(id)
        }
        val current = apiMap[id]
        if (current != null) {
            apiMap[id] = current.copy(enabled = enabled)
        }
        updateFlow()
    }

    fun recordApiCall(record: ApiCallRecord) {
        val current = _callHistoryFlow.value.toMutableList()
        current.add(0, record)
        if (current.size > 50) {
            current.removeAt(current.size - 1)
        }
        _callHistoryFlow.value = current
    }

    private fun updateFlow() {
        _apisFlow.value = apiMap.values.map {
            it.copy(enabled = !disabledApiIds.contains(it.id))
        }.sortedBy { it.name }
    }
}
