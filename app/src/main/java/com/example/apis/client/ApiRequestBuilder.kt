package com.example.apis.client

import com.example.apis.models.ApiDefinition
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale

/**
 * Builds OkHttp Request from ApiDefinition and user/system provided parameters.
 */
class ApiRequestBuilder {

    fun buildRequest(
        api: ApiDefinition,
        parameters: Map<String, String> = emptyMap(),
        bodyContent: String? = null
    ): Request {
        var processedEndpoint = api.endpoint

        // 1. Substitute Path Parameters: e.g. /api/fruit/{name} -> /api/fruit/apple
        val remainingParams = parameters.toMutableMap()
        for (paramDef in api.parameters) {
            val placeholder = "{${paramDef.name}}"
            if (processedEndpoint.contains(placeholder)) {
                val value = remainingParams[paramDef.name]
                    ?: remainingParams[paramDef.name.lowercase(Locale.ROOT)]
                    ?: paramDef.defaultValue
                val encodedValue = try {
                    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
                } catch (_: Exception) {
                    value
                }
                processedEndpoint = processedEndpoint.replace(placeholder, encodedValue)
                remainingParams.remove(paramDef.name)
            }
        }

        // Also substitute any remaining {var} in endpoint if present in remainingParams
        val regex = "\\{([a-zA-Z0-9_]+)\\}".toRegex()
        processedEndpoint = regex.replace(processedEndpoint) { matchResult ->
            val key = matchResult.groupValues[1]
            val value = remainingParams.remove(key) ?: remainingParams.remove(key.lowercase(Locale.ROOT)) ?: ""
            try {
                URLEncoder.encode(value, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                value
            }
        }

        // 2. Assemble Full Base URL + Endpoint
        val cleanBase = api.baseUrl.trimEnd('/')
        val cleanEndpoint = processedEndpoint.trimStart('/')
        val rawUrl = "$cleanBase/$cleanEndpoint"

        val parsedHttpUrl = rawUrl.toHttpUrlOrNull()
        val httpUrlBuilder: HttpUrl.Builder = if (parsedHttpUrl != null) {
            parsedHttpUrl.newBuilder()
        } else {
            HttpUrl.Builder()
                .scheme(if (api.https) "https" else "http")
                .host(cleanBase.removePrefix("https://").removePrefix("http://"))
                .addPathSegments(cleanEndpoint)
        }

        // 3. Append Query Parameters
        for (paramDef in api.parameters) {
            if (paramDef.location.equals("query", ignoreCase = true)) {
                val value = remainingParams[paramDef.name]
                    ?: remainingParams[paramDef.name.lowercase(Locale.ROOT)]
                    ?: paramDef.defaultValue
                if (value.isNotBlank() && !rawUrl.contains("${paramDef.name}=")) {
                    httpUrlBuilder.setQueryParameter(paramDef.name, value)
                    remainingParams.remove(paramDef.name)
                }
            }
        }

        // Any leftover parameters passed explicitly are added as query params
        for ((k, v) in remainingParams) {
            if (v.isNotBlank()) {
                httpUrlBuilder.setQueryParameter(k, v)
            }
        }

        val finalUrl = httpUrlBuilder.build()
        val requestBuilder = Request.Builder().url(finalUrl)

        // 4. Default Headers
        requestBuilder.header("User-Agent", "Myra-Assistant/2.0 (Android; Generic-API-Client)")
        requestBuilder.header("Accept", "application/json, text/plain, */*")

        // 5. HTTP Method & Body
        val method = api.httpMethod.uppercase(Locale.ROOT)
        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = (bodyContent ?: "{}").toRequestBody(mediaType)
                requestBuilder.post(body)
            }
            "PUT" -> {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = (bodyContent ?: "{}").toRequestBody(mediaType)
                requestBuilder.put(body)
            }
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        return requestBuilder.build()
    }
}
