package com.example.accessibility

import android.view.accessibility.AccessibilityNodeInfo

data class MatchedElement(
    val node: AccessibilityNodeInfo,
    val matchedField: String, // "TEXT", "CONTENT_DESCRIPTION", "RESOURCE_ID"
    val score: Float
)

class ElementMatcher {

    fun findBestMatch(rootNode: AccessibilityNodeInfo?, targetName: String): MatchedElement? {
        if (rootNode == null || targetName.isBlank()) return null

        val candidates = mutableListOf<MatchedElement>()
        val targetLower = targetName.lowercase().trim()

        val synonyms = getSynonyms(targetLower)

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val resId = node.viewIdResourceName?.lowercase() ?: ""

            var score = 0f
            var field = "NONE"

            // Exact match
            when {
                text == targetLower -> { score = 1.0f; field = "TEXT" }
                desc == targetLower -> { score = 0.98f; field = "CONTENT_DESCRIPTION" }
                synonyms.any { text == it } -> { score = 0.95f; field = "TEXT_SYNONYM" }
                synonyms.any { desc == it } -> { score = 0.94f; field = "DESC_SYNONYM" }
                text.contains(targetLower) -> { score = 0.85f; field = "TEXT_CONTAINS" }
                desc.contains(targetLower) -> { score = 0.84f; field = "DESC_CONTAINS" }
                resId.contains(targetLower) -> { score = 0.75f; field = "RESOURCE_ID" }
            }

            if (score > 0f) {
                // Clickable bonus
                val effectiveNode = if (node.isClickable) node else findClickableAncestor(node) ?: node
                candidates.add(MatchedElement(node = effectiveNode, matchedField = field, score = score))
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return candidates.maxByOrNull { it.score }
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent
            parent = parent.parent
        }
        return null
    }

    private fun getSynonyms(keyword: String): List<String> {
        return when (keyword) {
            "search", "सर्च", "खोजें" -> listOf("search", "search youtube", "खोजें", "सर्च करें", "find", "search_button")
            "play", "चलाओ", "प्ले" -> listOf("play", "play video", "प्ले", "चलाएं", "resume")
            "pause", "रोको", "पॉज़" -> listOf("pause", "pause video", "रोकें", "पॉज़ करें")
            "clear", "saaf", "साफ़" -> listOf("clear text", "clear", "delete", "मिटाएं")
            "submit", "enter", "go" -> listOf("submit", "go", "done", "enter", "search")
            else -> emptyList()
        }
    }
}
