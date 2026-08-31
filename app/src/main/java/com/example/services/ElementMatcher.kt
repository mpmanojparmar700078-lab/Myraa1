package com.example.services

import com.example.models.ElementMatchResult
import com.example.models.MatchConfidenceLevel
import com.example.models.UIElement
import java.util.Locale

/**
 * Hardened multi-signal UI Element Matcher for Android Accessibility Nodes.
 *
 * Capabilities:
 * 1. Multi-signal scoring (exact, case-insensitive, normalized, tokenized, fuzzy, bounds, role).
 * 2. Confidence level calculation (HIGH >= 0.85, MEDIUM >= 0.60, LOW >= 0.35, NONE < 0.35).
 * 3. Ambiguity detection: checks if competing elements have very close high scores to prevent misclicks.
 * 4. Text normalization and punctuation stripping.
 * 5. Comprehensive bilingual (Hindi / Hinglish / English) synonym maps.
 * 6. Specialized resilient matchers for common Android app workflows.
 */
object ElementMatcher {

    data class MatchOptions(
        val exactMatch: Boolean = false,
        val requireClickable: Boolean = false,
        val requireEditable: Boolean = false,
        val requireEnabled: Boolean = true,
        val checkSynonyms: Boolean = true,
        val targetClassHint: String? = null
    )

    private val SYNONYM_DICTIONARY: Map<String, Set<String>> = mapOf(
        "search" to setOf("search", "find", "query", "explore", "lookup", "खोजें", "सर्च", "ढूँढें", "ढूंढें", "search youtube", "type a search"),
        "play" to setOf("play", "resume", "start", "चलाएं", "चालू करें", "प्ले", "बजाओ", "play video"),
        "pause" to setOf("pause", "hold", "रोकें", "विराम", "pause video"),
        "clear" to setOf("clear", "delete", "erase", "remove", "reset", "हटाएं", "साफ़ करें", "मिटाएं", "clear query", "clear search"),
        "back" to setOf("back", "navigate up", "previous", "पीछे", "वापस"),
        "next" to setOf("next", "forward", "अगला", "आगे"),
        "submit" to setOf("submit", "send", "done", "enter", "go", "search", "भेजें", "पूरा"),
        "close" to setOf("close", "dismiss", "cancel", "बंद करें", "रद्द करें")
    )

    /**
     * Normalizes text by trimming, lowercasing, and removing punctuation.
     */
    fun normalizeText(text: String?): String {
        if (text == null) return ""
        return text.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Scores a UIElement against a target query on a 0.0 to 1.0 scale.
     */
    fun scoreElement(element: UIElement, target: String, options: MatchOptions = MatchOptions()): Float {
        if (options.requireEnabled && !element.isEnabled) return 0.0f
        if (options.requireClickable && !element.isClickable) return 0.0f
        if (options.requireEditable && !element.isEditable) return 0.0f

        val rawTarget = target.trim()
        if (rawTarget.isBlank()) return 0.0f

        val targetLower = rawTarget.lowercase(Locale.ROOT)
        val targetNorm = normalizeText(rawTarget)

        val elTextLower = element.text?.lowercase(Locale.ROOT)?.trim() ?: ""
        val elTextNorm = normalizeText(element.text)

        val elDescLower = element.contentDescription?.lowercase(Locale.ROOT)?.trim() ?: ""
        val elDescNorm = normalizeText(element.contentDescription)

        val elViewIdLower = element.viewId?.lowercase(Locale.ROOT)?.trim() ?: ""
        val elViewIdShort = elViewIdLower.substringAfterLast("/")

        var score = 0.0f

        // 1. Exact matches (Score: 0.90 - 1.00)
        if (elTextLower == targetLower || elDescLower == targetLower) {
            score = 1.0f
        } else if (elTextNorm == targetNorm || elDescNorm == targetNorm) {
            score = 0.95f
        } else if (elViewIdShort == targetLower || elViewIdLower == targetLower) {
            score = 0.92f
        }

        // If exact match requested, return score now
        if (options.exactMatch) {
            return if (score >= 0.90f) score else 0.0f
        }

        // 2. Substring & Prefix matches
        if (score < 0.90f) {
            val containsInText = elTextLower.contains(targetLower) || (elTextNorm.isNotEmpty() && elTextNorm.contains(targetNorm))
            val containsInDesc = elDescLower.contains(targetLower) || (elDescNorm.isNotEmpty() && elDescNorm.contains(targetNorm))
            val containsInViewId = elViewIdLower.contains(targetLower) || elViewIdShort.contains(targetLower)

            if (containsInText || containsInDesc) {
                val lenDiff = kotlin.math.abs((element.text ?: element.contentDescription ?: "").length - rawTarget.length)
                val proximityBonus = (1.0f / (1.0f + lenDiff * 0.05f)).coerceIn(0.0f, 0.15f)
                score = (0.75f + proximityBonus).coerceAtMost(0.88f)
            } else if (containsInViewId) {
                score = 0.70f
            }
        }

        // 3. Token-level matching
        if (score < 0.70f && targetNorm.length > 2) {
            val targetTokens = targetNorm.split(" ").filter { it.length >= 2 }
            val elementTokens = (elTextNorm + " " + elDescNorm).split(" ").filter { it.length >= 2 }

            if (targetTokens.isNotEmpty() && elementTokens.isNotEmpty()) {
                var matched = 0
                for (t in targetTokens) {
                    if (elementTokens.any { it == t || it.contains(t) || t.contains(it) }) {
                        matched++
                    }
                }
                val tokenRatio = matched.toFloat() / targetTokens.size.toFloat()
                if (tokenRatio > 0.49f) {
                    score = (0.50f + (tokenRatio * 0.25f)).coerceAtMost(0.72f)
                }
            }
        }

        // 4. Synonym dictionary check
        if (score < 0.70f && options.checkSynonyms) {
            for ((_, synonyms) in SYNONYM_DICTIONARY) {
                val targetIsSynonym = synonyms.any { it == targetLower || it == targetNorm }
                if (targetIsSynonym) {
                    val elementMatchesSynonym = synonyms.any { syn ->
                        elTextLower.contains(syn) || elDescLower.contains(syn) || elViewIdLower.contains(syn)
                    }
                    if (elementMatchesSynonym) {
                        score = score.coerceAtLeast(0.80f)
                        break
                    }
                }
            }
        }

        // 5. Class hint bonus
        if (options.targetClassHint != null && score > 0.3f) {
            val elClass = element.className?.lowercase(Locale.ROOT) ?: ""
            if (elClass.contains(options.targetClassHint.lowercase(Locale.ROOT))) {
                score = (score + 0.05f).coerceAtMost(1.0f)
            }
        }

        // 6. Clickable bonus
        if (element.isClickable && score > 0.4f) {
            score = (score + 0.05f).coerceAtMost(1.0f)
        }

        return score
    }

    /**
     * Evaluates a list of UI elements and selects the best matching candidate with ambiguity detection.
     */
    fun findBestMatch(
        elements: List<UIElement>,
        target: String,
        options: MatchOptions = MatchOptions()
    ): ElementMatchResult {
        if (elements.isEmpty() || target.isBlank()) {
            return ElementMatchResult(
                element = null,
                score = 0.0f,
                confidenceLevel = MatchConfidenceLevel.NONE,
                isAmbiguous = false,
                matchReason = "No elements or blank target"
            )
        }

        val scoredList = elements.map { el ->
            Pair(el, scoreElement(el, target, options))
        }.filter { it.second >= MatchConfidenceLevel.LOW.threshold }
            .sortedByDescending { it.second }

        if (scoredList.isEmpty()) {
            return ElementMatchResult(
                element = null,
                score = 0.0f,
                confidenceLevel = MatchConfidenceLevel.NONE,
                isAmbiguous = false,
                matchReason = "No matching element found for '$target'"
            )
        }

        val top = scoredList.first()
        val topScore = top.second
        val topElement = top.first

        val confidence = when {
            topScore >= MatchConfidenceLevel.HIGH.threshold -> MatchConfidenceLevel.HIGH
            topScore >= MatchConfidenceLevel.MEDIUM.threshold -> MatchConfidenceLevel.MEDIUM
            topScore >= MatchConfidenceLevel.LOW.threshold -> MatchConfidenceLevel.LOW
            else -> MatchConfidenceLevel.NONE
        }

        // Ambiguity check: if top 2 candidates have very close scores (diff < 0.04) and high relevance
        val isAmbiguous = if (scoredList.size >= 2 && topScore >= MatchConfidenceLevel.MEDIUM.threshold) {
            val second = scoredList[1]
            val scoreDiff = topScore - second.second
            scoreDiff < 0.04f && second.second >= MatchConfidenceLevel.MEDIUM.threshold
        } else {
            false
        }

        val competing = if (isAmbiguous) scoredList.take(3).map { it.first } else emptyList()
        val reason = when {
            isAmbiguous -> "Ambiguous match: multiple elements matched '$target' with similar confidence."
            confidence == MatchConfidenceLevel.HIGH -> "High confidence match (${(topScore * 100).toInt()}%) for '${topElement.displayLabel}'."
            confidence == MatchConfidenceLevel.MEDIUM -> "Medium confidence match (${(topScore * 100).toInt()}%) for '${topElement.displayLabel}'."
            else -> "Low confidence match (${(topScore * 100).toInt()}%) for '${topElement.displayLabel}'."
        }

        return ElementMatchResult(
            element = topElement,
            score = topScore,
            confidenceLevel = confidence,
            isAmbiguous = isAmbiguous,
            competingMatches = competing,
            matchReason = reason
        )
    }

    /**
     * Legacy boolean matcher for simple lambda use.
     */
    fun matches(
        element: UIElement,
        target: String,
        exactMatch: Boolean = false,
        requireClickable: Boolean = false,
        requireEditable: Boolean = false
    ): Boolean {
        val score = scoreElement(
            element = element,
            target = target,
            options = MatchOptions(
                exactMatch = exactMatch,
                requireClickable = requireClickable,
                requireEditable = requireEditable
            )
        )
        return score >= (if (exactMatch) 0.90f else 0.60f)
    }

    /**
     * Matcher for YouTube Search button or Search icon in top bar.
     * Supports English ("Search", "Search YouTube", "search_button") and Hindi ("सर्च", "खोजें", "यूट्यूब खोजें").
     */
    fun forYouTubeSearchButton(): (UIElement) -> Boolean = { element ->
        val desc = element.contentDescription?.lowercase(Locale.ROOT) ?: ""
        val text = element.text?.lowercase(Locale.ROOT) ?: ""
        val viewId = element.viewId?.lowercase(Locale.ROOT) ?: ""
        val className = element.className?.lowercase(Locale.ROOT) ?: ""

        val isSearchKeyword = desc.contains("search") ||
                desc.contains("search youtube") ||
                desc.contains("सर्च") ||
                desc.contains("खोजें") ||
                desc.contains("ढूंढें") ||
                text.contains("search") ||
                text.contains("सर्च") ||
                text.contains("खोजें") ||
                viewId.contains("menu_item_search") ||
                viewId.contains("search_button") ||
                viewId.contains("search_src_text") ||
                viewId.contains("action_search")

        val isClickableOrButton = element.isClickable ||
                className.contains("imageview") ||
                className.contains("button") ||
                className.contains("view")

        isSearchKeyword && isClickableOrButton
    }

    /**
     * Matcher for Search Input / EditText field across apps.
     */
    fun forSearchInputField(): (UIElement) -> Boolean = { element ->
        val desc = element.contentDescription?.lowercase(Locale.ROOT) ?: ""
        val text = element.text?.lowercase(Locale.ROOT) ?: ""
        val viewId = element.viewId?.lowercase(Locale.ROOT) ?: ""
        val className = element.className?.lowercase(Locale.ROOT) ?: ""

        val isEditText = element.isEditable ||
                className.contains("edittext") ||
                className.contains("autocompletetextview") ||
                className.contains("searchautocomplete")

        val hasSearchHintOrId = isEditText ||
                viewId.contains("search_edit_text") ||
                viewId.contains("search_src_text") ||
                viewId.contains("search_input") ||
                viewId.contains("query") ||
                desc.contains("search query") ||
                desc.contains("search box") ||
                desc.contains("search youtube") ||
                desc.contains("type a search") ||
                text.contains("search") ||
                text.contains("खोजें")

        hasSearchHintOrId && (element.isEnabled)
    }

    /**
     * Matcher for YouTube Search Result Items (videos, playlists, cards).
     */
    fun forYouTubeResultItem(): (UIElement) -> Boolean = { element ->
        val text = element.text?.trim() ?: ""
        val desc = element.contentDescription?.trim() ?: ""
        val className = element.className?.lowercase(Locale.ROOT) ?: ""

        val hasContent = (text.length > 3 || desc.length > 5)
        val notControlHeader = !text.equals("Search", ignoreCase = true) &&
                !desc.contains("Search YouTube", ignoreCase = true) &&
                !text.equals("Home", ignoreCase = true) &&
                !text.equals("Subscriptions", ignoreCase = true) &&
                !text.equals("You", ignoreCase = true)

        hasContent && notControlHeader && (element.isClickable || className.contains("viewgroup") || className.contains("layout") || text.isNotBlank())
    }

    /**
     * Matcher for YouTube Video Watch / Player Screen Elements.
     */
    fun forYouTubeVideoPlayer(): (UIElement) -> Boolean = { element ->
        val desc = element.contentDescription?.lowercase(Locale.ROOT) ?: ""
        val text = element.text?.lowercase(Locale.ROOT) ?: ""
        val viewId = element.viewId?.lowercase(Locale.ROOT) ?: ""
        val className = element.className?.lowercase(Locale.ROOT) ?: ""

        viewId.contains("player") ||
                viewId.contains("watch_player") ||
                viewId.contains("exo_player") ||
                viewId.contains("surface_view") ||
                viewId.contains("video_surface") ||
                viewId.contains("fullscreen_button") ||
                viewId.contains("playback_control") ||
                desc.contains("player") ||
                desc.contains("video player") ||
                desc.contains("pause video") ||
                desc.contains("play video") ||
                className.contains("surfaceview") ||
                className.contains("textureview") ||
                className.contains("player")
    }

    /**
     * Matcher for YouTube Play button (to start or resume playback).
     */
    fun forYouTubePlayButton(): (UIElement) -> Boolean = { element ->
        val desc = element.contentDescription?.lowercase(Locale.ROOT) ?: ""
        val text = element.text?.lowercase(Locale.ROOT) ?: ""
        val viewId = element.viewId?.lowercase(Locale.ROOT) ?: ""

        (desc.contains("play video") || desc == "play" || desc.contains("play") ||
                text == "play" || desc.contains("चलाएं") || desc.contains("चालू करें") ||
                viewId.contains("play_button") || viewId.contains("btn_play")) &&
                !desc.contains("playlist") && !desc.contains("play next") && !desc.contains("autoplay")
    }

    /**
     * Matcher for YouTube Pause button (indicating video is currently active/playing).
     */
    fun forYouTubePauseButton(): (UIElement) -> Boolean = { element ->
        val desc = element.contentDescription?.lowercase(Locale.ROOT) ?: ""
        val text = element.text?.lowercase(Locale.ROOT) ?: ""
        val viewId = element.viewId?.lowercase(Locale.ROOT) ?: ""

        desc.contains("pause video") || desc == "pause" || desc.contains("रोकें") ||
                text == "pause" || viewId.contains("pause_button") || viewId.contains("btn_pause")
    }

    /**
     * Matcher for a generic button with specified labels.
     */
    fun forButtonWithLabels(vararg labels: String): (UIElement) -> Boolean = { element ->
        val targetLabels = labels.map { it.lowercase(Locale.ROOT).trim() }
        val textLower = element.text?.lowercase(Locale.ROOT)?.trim()
        val descLower = element.contentDescription?.lowercase(Locale.ROOT)?.trim()
        val viewIdLower = element.viewId?.lowercase(Locale.ROOT)?.trim()

        targetLabels.any { label ->
            (textLower != null && (textLower == label || textLower.contains(label))) ||
            (descLower != null && (descLower == label || descLower.contains(label))) ||
            (viewIdLower != null && viewIdLower.contains(label))
        }
    }
}
