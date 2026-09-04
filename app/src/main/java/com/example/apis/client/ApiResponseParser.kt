package com.example.apis.client

import com.example.apis.models.ApiDefinition
import com.example.apis.models.ApiResponse
import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalizes raw JSON responses from various public APIs into standard ApiResponse objects
 * using the configured responseMapping in ApiDefinition.
 */
class ApiResponseParser {

    fun parse(
        api: ApiDefinition,
        statusCode: Int,
        rawJson: String,
        latencyMs: Long,
        isCached: Boolean = false
    ): ApiResponse {
        if (rawJson.isBlank()) {
            return ApiResponse(
                success = statusCode in 200..299,
                statusCode = statusCode,
                rawJson = "",
                extractedData = emptyMap(),
                formattedSummary = "Empty response received.",
                apiId = api.id,
                apiName = api.name,
                latencyMs = latencyMs,
                isCached = isCached
            )
        }

        val extracted = mutableMapOf<String, Any?>()

        try {
            val rootObj: Any = if (rawJson.trim().startsWith("[")) {
                JSONArray(rawJson)
            } else {
                JSONObject(rawJson)
            }

            for ((field, path) in api.responseMapping) {
                val value = extractValueByPath(rootObj, path)
                if (value != null) {
                    extracted[field] = value
                }
            }
        } catch (e: Exception) {
            // If strict JSON parsing fails, extract raw string
            extracted["rawText"] = rawJson.take(300)
        }

        val summary = generateFormattedSummary(api, extracted, rawJson)

        return ApiResponse(
            success = statusCode in 200..299,
            statusCode = statusCode,
            rawJson = rawJson,
            extractedData = extracted,
            formattedSummary = summary,
            apiId = api.id,
            apiName = api.name,
            latencyMs = latencyMs,
            isCached = isCached
        )
    }

    /**
     * Extracts values using lightweight JSONPath-like syntax:
     * Examples: "$.joke", "$.setup", "$.delivery", "$[0].title", "$.nutritions.calories"
     */
    private fun extractValueByPath(root: Any, path: String): Any? {
        try {
            var current: Any? = root
            val cleanPath = path.removePrefix("$").removePrefix(".")
            if (cleanPath.isBlank()) return root

            val tokens = splitPathTokens(cleanPath)
            for (token in tokens) {
                if (current == null) return null

                if (token.startsWith("[") && token.endsWith("]")) {
                    val index = token.substring(1, token.length - 1).toIntOrNull() ?: return null
                    current = if (current is JSONArray && index < current.length()) {
                        current.get(index)
                    } else {
                        null
                    }
                } else {
                    current = if (current is JSONObject) {
                        current.opt(token)
                    } else {
                        null
                    }
                }
            }
            return current
        } catch (_: Exception) {
            return null
        }
    }

    private fun splitPathTokens(path: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < path.length) {
            val c = path[i]
            if (c == '.') {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
            } else if (c == '[') {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
                val closeBracket = path.indexOf(']', i)
                if (closeBracket != -1) {
                    tokens.add(path.substring(i, closeBracket + 1))
                    i = closeBracket
                }
            } else {
                sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    private fun generateFormattedSummary(
        api: ApiDefinition,
        extracted: Map<String, Any?>,
        rawJson: String
    ): String {
        return when (api.id) {
            "jokeapi" -> {
                val single = extracted["single"]?.toString()
                val setup = extracted["setup"]?.toString()
                val delivery = extracted["delivery"]?.toString()
                when {
                    !single.isNullOrBlank() -> single
                    !setup.isNullOrBlank() && !delivery.isNullOrBlank() -> "$setup\n\n$delivery"
                    else -> rawJson.take(200)
                }
            }

            "chucknorris" -> {
                extracted["text"]?.toString() ?: rawJson.take(200)
            }

            "fruityvice" -> {
                val title = extracted["title"]?.toString()?.replaceFirstChar { it.uppercase() } ?: "Fruit"
                val calories = extracted["calories"]?.toString() ?: "-"
                val sugar = extracted["sugar"]?.toString() ?: "-"
                val carbs = extracted["carbohydrates"]?.toString() ?: "-"
                val family = extracted["family"]?.toString() ?: "-"
                "$title (Family: $family)\n• Calories: $calories kcal\n• Carbohydrates: ${carbs}g\n• Sugar: ${sugar}g"
            }

            "cat_facts", "dog_facts" -> {
                extracted["text"]?.toString() ?: rawJson.take(200)
            }

            "open_trivia" -> {
                val q = extracted["question"]?.toString() ?: "Question"
                val a = extracted["answer"]?.toString() ?: "Answer"
                val cat = extracted["category"]?.toString() ?: "General"
                "[$cat Trivia]\nQ: $q\n\nAnswer: $a"
            }

            "bored_api" -> {
                val act = extracted["text"]?.toString() ?: "Activity"
                val type = extracted["type"]?.toString() ?: "general"
                "Activity: $act (Type: $type)"
            }

            "quotable" -> {
                val quote = extracted["text"]?.toString() ?: ""
                val author = extracted["author"]?.toString() ?: "Unknown"
                "\"$quote\"\n— $author"
            }

            "freetogame" -> {
                val title = extracted["title"]?.toString() ?: "Free Game"
                val genre = extracted["genre"]?.toString() ?: ""
                val platform = extracted["platform"]?.toString() ?: ""
                val desc = extracted["description"]?.toString() ?: ""
                "$title ($genre on $platform)\n$desc"
            }

            "open_meteo" -> {
                val temp = extracted["temperature"]?.toString() ?: "--"
                val hum = extracted["humidity"]?.toString() ?: "--"
                val wind = extracted["windSpeed"]?.toString() ?: "--"
                "Current Weather:\n• Temperature: $temp°C\n• Humidity: $hum%\n• Wind: $wind km/h"
            }

            "wttr_in" -> {
                val temp = extracted["temperature"]?.toString() ?: "--"
                val desc = extracted["description"]?.toString() ?: ""
                val hum = extracted["humidity"]?.toString() ?: "--"
                val wind = extracted["windSpeed"]?.toString() ?: "--"
                "Weather ($desc):\n• Temperature: $temp°C\n• Humidity: $hum%\n• Wind: $wind km/h"
            }

            "rest_countries" -> {
                val name = extracted["name"]?.toString() ?: "Country"
                val cap = extracted["capital"]?.toString() ?: "N/A"
                val pop = extracted["population"]?.toString() ?: "N/A"
                val reg = extracted["region"]?.toString() ?: "N/A"
                val flag = extracted["flag"]?.toString() ?: ""
                "$flag $name\n• Capital: $cap\n• Region: $reg\n• Population: $pop"
            }

            "spacex" -> {
                val mission = extracted["mission"]?.toString() ?: "Mission"
                val details = extracted["details"]?.toString() ?: "No additional details."
                val flightNum = extracted["flightNumber"]?.toString() ?: ""
                "SpaceX Launch #$flightNum: $mission\n$details"
            }

            "agify" -> {
                val name = extracted["name"]?.toString() ?: "Name"
                val age = extracted["age"]?.toString() ?: "Unknown"
                "Estimated age for $name: $age years"
            }

            "nationalize" -> {
                val name = extracted["name"]?.toString() ?: "Name"
                val country = extracted["country"]?.toString() ?: "Unknown"
                val prob = extracted["probability"]?.toString() ?: "0.0"
                "Nationality for $name: Most likely $country (Confidence: $prob)"
            }

            "coingecko_simple" -> {
                val btcUsd = extracted["btcUsd"]?.toString() ?: "--"
                val btcInr = extracted["btcInr"]?.toString() ?: "--"
                val ethUsd = extracted["ethUsd"]?.toString() ?: "--"
                val ethInr = extracted["ethInr"]?.toString() ?: "--"
                "Live Crypto Prices:\n• Bitcoin (BTC): $$btcUsd (₹$btcInr)\n• Ethereum (ETH): $$ethUsd (₹$ethInr)"
            }

            "dictionary" -> {
                val word = extracted["word"]?.toString() ?: ""
                val pos = extracted["partOfSpeech"]?.toString() ?: ""
                val def = extracted["definition"]?.toString() ?: ""
                val ex = extracted["example"]?.toString()
                val exampleText = if (!ex.isNullOrBlank()) "\nExample: \"$ex\"" else ""
                "$word ($pos)\n$def$exampleText"
            }

            "adviceslip" -> {
                extracted["text"]?.toString() ?: rawJson.take(200)
            }

            "numbersapi" -> {
                extracted["text"]?.toString() ?: rawJson.take(200)
            }

            "randomuser" -> {
                val name = "${extracted["name"]} ${extracted["lastName"]}"
                val email = extracted["email"]?.toString() ?: ""
                val country = extracted["country"]?.toString() ?: ""
                "Random User: $name\nEmail: $email\nCountry: $country"
            }

            "universities" -> {
                val name = extracted["name"]?.toString() ?: "University"
                val country = extracted["country"]?.toString() ?: ""
                val web = extracted["webPage"]?.toString() ?: ""
                "$name ($country)\nWebsite: $web"
            }

            "sunrise_sunset" -> {
                val sunrise = extracted["sunrise"]?.toString() ?: "--"
                val sunset = extracted["sunset"]?.toString() ?: "--"
                val dayLength = extracted["dayLength"]?.toString() ?: "--"
                "Solar Times:\n• Sunrise: $sunrise\n• Sunset: $sunset\n• Day Length: $dayLength"
            }

            else -> {
                val text = extracted["text"] ?: extracted["description"] ?: extracted["title"]
                text?.toString() ?: rawJson.take(250)
            }
        }
    }
}
