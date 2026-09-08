package com.example.brain

import com.example.models.ContextReference
import com.example.models.ContextReferenceType

/**
 * ContextResolver resolves references to previous requests:
 * - "1 wale me tumne kya kiya" -> index 1
 * - "pehle wala", "jo maine pehle bola" -> index 1 / last
 * - "usme", "usko", "wo wala", "usi ko" -> last request
 */
object ContextResolver {

    private val indexPattern = Regex("(?i)\\b(\\d+)\\s*(?:wale|waale|wala|no|number|waala)\\b")
    private val devanagariIndexPattern = Regex("([१२३४५६७८९]+)\\s*(?:वाले|वाला)?")

    fun extractReference(normalizedText: String): ContextReference? {
        val lower = normalizedText.lowercase()

        // 1. Explicit Numbered Request: "1 wale me", "2 wala", "3rd wala", "number 1"
        val numMatch = indexPattern.find(lower)
        if (numMatch != null) {
            val idx = numMatch.groupValues[1].toIntOrNull()
            if (idx != null) {
                return ContextReference(
                    type = ContextReferenceType.REQUEST_INDEX,
                    index = idx,
                    rawRef = numMatch.value
                )
            }
        }

        // Devanagari numbers
        val devMatch = devanagariIndexPattern.find(normalizedText)
        if (devMatch != null) {
            val devNum = devMatch.groupValues[1]
            val mapped = devanagariToInteger(devNum)
            if (mapped != null) {
                return ContextReference(
                    type = ContextReferenceType.REQUEST_INDEX,
                    index = mapped,
                    rawRef = devMatch.value
                )
            }
        }

        // Word-based ordinals
        if (lower.contains("pehle wala") || lower.contains("pehla wala") || lower.contains("first wala") ||
            lower.contains("first one") || lower.contains("1st wala")
        ) {
            return ContextReference(
                type = ContextReferenceType.REQUEST_INDEX,
                index = 1,
                rawRef = "pehle wala"
            )
        }

        if (lower.contains("doosra wala") || lower.contains("dusra wala") || lower.contains("second wala") ||
            lower.contains("2nd wala")
        ) {
            return ContextReference(
                type = ContextReferenceType.REQUEST_INDEX,
                index = 2,
                rawRef = "doosra wala"
            )
        }

        if (lower.contains("teesra wala") || lower.contains("tisra wala") || lower.contains("third wala") ||
            lower.contains("3rd wala")
        ) {
            return ContextReference(
                type = ContextReferenceType.REQUEST_INDEX,
                index = 3,
                rawRef = "teesra wala"
            )
        }

        // Relative / Anaphoric references: "jo maine pehle bola", "pichla", "last wala"
        if (lower.contains("jo maine pehle bola") || lower.contains("jo maine pehle kaha") ||
            lower.contains("pichla wala") || lower.contains("last wala") || lower.contains("previous wala")
        ) {
            return ContextReference(
                type = ContextReferenceType.RELATIVE,
                index = 1,
                rawRef = "pichla wala"
            )
        }

        // Direct pronouns: "usme", "usko", "wo wala", "usi ko"
        val pronounPhrases = listOf("usme", "usko", "wo wala", "woh wala", "usi ko", "usi me", "usme se")
        for (phrase in pronounPhrases) {
            if (lower.contains(phrase)) {
                return ContextReference(
                    type = ContextReferenceType.LAST_REQUEST,
                    rawRef = phrase
                )
            }
        }

        return null
    }

    private fun devanagariToInteger(s: String): Int? {
        val sb = StringBuilder()
        for (ch in s) {
            val d = when (ch) {
                '०' -> '0'
                '१' -> '1'
                '२' -> '2'
                '३' -> '3'
                '४' -> '4'
                '५' -> '5'
                '६' -> '6'
                '७' -> '7'
                '८' -> '8'
                '९' -> '9'
                else -> null
            }
            if (d != null) sb.append(d)
        }
        return sb.toString().toIntOrNull()
    }
}
