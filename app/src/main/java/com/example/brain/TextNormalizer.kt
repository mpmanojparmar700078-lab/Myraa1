package com.example.brain

/**
 * Safe text normalization layer for Myra.
 * Normalizes:
 * - Extra spaces
 * - Repeated punctuation
 * - Common Hindi/Hinglish variations and contractions
 * - English/Hindi mixed script
 *
 * CRITICAL RULE:
 * Never removes meaningful words from the original message.
 * Always preserve rawUserMessage.
 */
object TextNormalizer {

    private val spellingVariations = listOf(
        Regex("(?i)\\bplaykro\\b") to "play karo",
        Regex("(?i)\\bplay\\s+kro\\b") to "play karo",
        Regex("(?i)\\bkro\\b") to "karo",
        Regex("(?i)\\bkardo\\b") to "kar do",
        Regex("(?i)\\bchalado\\b") to "chala do",
        Regex("(?i)\\bchala\\s+do\\b") to "chala do",
        Regex("(?i)\\bkholkar\\b") to "khol kar",
        Regex("(?i)\\bkholdo\\b") to "khol do",
        Regex("(?i)\\bbhi\\s+nhi\\b") to "bhi nahi",
        Regex("(?i)\\bnhi\\b") to "nahi",
        Regex("(?i)\\bplz\\b") to "please",
        Regex("(?i)\\byt\\b") to "youtube",
        Regex("(?i)\\bcraft\\s+land\\b") to "craftland",
        Regex("(?i)\\bpehle\\s+vala\\b") to "pehle wala"
    )

    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // 1. Collapse multiple spaces / tabs / newlines to single space
        text = text.replace(Regex("\\s+"), " ")

        // 2. Normalize repeated punctuation: "???", "!!!", "...." -> single punctuation
        text = text.replace(Regex("\\?+"), "?")
        text = text.replace(Regex("!+"), "!")
        text = text.replace(Regex("\\.{2,}"), "...")

        // 3. Lowercase English tokens while preserving Devanagari characters
        text = text.lowercase()

        // 4. Normalize common Hinglish token combinations
        for ((pattern, replacement) in spellingVariations) {
            text = pattern.replace(text, replacement)
        }

        // Final trim and whitespace compaction
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
