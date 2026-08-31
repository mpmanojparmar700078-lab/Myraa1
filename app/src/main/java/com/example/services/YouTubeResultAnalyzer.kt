package com.example.services

import com.example.models.ScreenState
import com.example.models.UIElement
import com.example.models.VideoCandidate
import com.example.models.VideoCandidateType
import com.example.models.YouTubeResultSelection
import java.util.Locale

/**
 * Intelligent Local YouTube Result Analyzer for Myra V3.3.
 *
 * Responsibilities:
 * 1. Reads accessibility tree elements and extracts candidate video results.
 * 2. Classifies candidates into VIDEO, SHORT, AD, CHANNEL, PLAYLIST.
 * 3. Rejects unsuitable items (Ads, Sponsored, Shorts, Channel profiles, Playlists, empty/control nodes).
 * 4. Computes deterministic local relevance scores against the user search query.
 * 5. Selects the most relevant genuine video result with clear confidence scoring.
 * 6. Guarantees 0 Gemini API calls during result extraction & analysis.
 */
object YouTubeResultAnalyzer {

    // Configurable thresholds for V3.3
    var MIN_RELEVANCE_THRESHOLD = 0.40f
    var MIN_VIDEO_CONFIDENCE = 0.70f

    private val STOPWORDS = setOf(
        "ka", "ki", "ke", "का", "की", "के", "me", "mein", "में", "par", "पर", "pe", "पे", "ko", "को",
        "the", "a", "an", "on", "in", "by", "of", "and", "or", "for", "to", "with",
        "karo", "kro", "karo", "do", "दो", "bhi", "भी"
    )

    /**
     * Analyzes search results on the active screen for the specified search query.
     */
    fun analyzeSearchResults(
        screenState: ScreenState,
        query: String,
        contextUsed: Boolean = false,
        contextAgeSeconds: Long = 0L
    ): YouTubeResultSelection {
        val rawElements = screenState.elements
        val candidates = mutableListOf<VideoCandidate>()
        val seenTitles = mutableSetOf<String>()

        for (element in rawElements) {
            val rawText = (element.text ?: element.contentDescription)?.trim() ?: ""
            if (rawText.isBlank() || rawText.length < 3) continue

            // Exclude common static navigation controls / header items
            if (isStaticControlOrNav(rawText, element)) continue

            val type = classifyCandidateType(rawText, element)
            val cleanTitle = extractCleanTitle(rawText)

            if (cleanTitle.isBlank() || seenTitles.contains(cleanTitle.lowercase(Locale.ROOT))) {
                continue
            }
            seenTitles.add(cleanTitle.lowercase(Locale.ROOT))

            val (score, rejectionReason) = computeRelevanceScore(cleanTitle, rawText, query, type)
            val confidence = calculateCandidateConfidence(score, type, element)

            candidates.add(
                VideoCandidate(
                    title = cleanTitle,
                    element = element,
                    rawText = rawText,
                    type = type,
                    relevanceScore = score,
                    isClickable = element.isClickable,
                    confidence = confidence,
                    rejectionReason = rejectionReason
                )
            )
        }

        // Sort candidates: Selectable videos first by highest relevance score and confidence
        val sortedCandidates = candidates.sortedWith(
            compareByDescending<VideoCandidate> { it.isSelectable }
                .thenByDescending { it.relevanceScore }
                .thenByDescending { it.confidence }
        )

        val eligibleVideos = sortedCandidates.filter { it.isSelectable && it.confidence >= MIN_VIDEO_CONFIDENCE }

        val isAmbiguous = if (eligibleVideos.size >= 2) {
            val top1 = eligibleVideos[0]
            val top2 = eligibleVideos[1]
            kotlin.math.abs(top1.relevanceScore - top2.relevanceScore) < 0.05f
        } else {
            false
        }

        val bestCandidate = if (!isAmbiguous) eligibleVideos.firstOrNull() else null

        val message = when {
            isAmbiguous -> "मुझे दो समान वीडियो मिले: \"${eligibleVideos[0].title}\" और \"${eligibleVideos[1].title}\"। कृपया स्पष्ट करें कि आप कौन सा वीडियो चलाना चाहते हैं।"
            bestCandidate != null -> "सर्वश्रेष्ठ वीडियो चुना गया: \"${bestCandidate.title}\" (Relevance: ${(bestCandidate.relevanceScore * 100).toInt()}%)"
            sortedCandidates.isEmpty() -> "स्क्रीन पर कोई वीडियो परिणाम नहीं मिला।"
            sortedCandidates.none { it.type == VideoCandidateType.VIDEO } -> "स्क्रीन पर केवल Shorts/Ads मिले, उपयुक्त वीडियो नहीं मिला।"
            else -> "मुझे सही वीडियो पहचानने में भरोसा नहीं है।"
        }

        return YouTubeResultSelection(
            query = query,
            intent = "YOUTUBE_SEARCH_AND_PLAY",
            candidates = sortedCandidates,
            selected = bestCandidate,
            isAmbiguous = isAmbiguous,
            message = message,
            contextUsed = contextUsed,
            contextAgeSeconds = contextAgeSeconds,
            geminiRequests = 0
        )
    }

    /**
     * Determines whether an element represents an Ad, Short, Channel, Playlist, or standard Video.
     */
    fun classifyCandidateType(rawText: String, element: UIElement): VideoCandidateType {
        val lower = rawText.lowercase(Locale.ROOT).trim()
        val descLower = (element.contentDescription ?: "").lowercase(Locale.ROOT).trim()
        val viewIdLower = (element.viewId ?: "").lowercase(Locale.ROOT).trim()

        // 0. Exclude UI Controls and Editable input fields
        if (element.isEditable || element.className?.contains("EditText", ignoreCase = true) == true ||
            viewIdLower.contains("search_edit_text") || viewIdLower.contains("search_src_text") ||
            viewIdLower.contains("search_clear") || viewIdLower.contains("clear_search")) {
            return VideoCandidateType.UNKNOWN
        }

        // 1. Advertisements & Sponsored Content
        val isAd = lower == "ad" || lower.startsWith("ad •") || lower.startsWith("ad ·") ||
                lower.startsWith("ad -") || lower.startsWith("ad: ") || lower.contains(" sponsored") ||
                lower.startsWith("sponsored") || lower.contains("प्रायोजित") || lower.contains("বিজ্ঞাপন") ||
                lower.contains("ads by") || descLower.contains("sponsored") || descLower.contains("advertisement") ||
                viewIdLower.contains("promoted") || viewIdLower.contains("ad_badge") || viewIdLower.contains("ad_attribution")
        if (isAd) {
            return VideoCandidateType.AD
        }

        // 2. Shorts Content
        val isShort = lower.contains("shorts") || lower.contains("#shorts") || lower.contains("शॉर्ट्स") ||
                descLower.contains("shorts") || descLower.contains("youtube shorts") ||
                viewIdLower.contains("shorts") || viewIdLower.contains("reel")
        if (isShort) {
            return VideoCandidateType.SHORT
        }

        // 3. Channels / Profiles
        // Distinguish actual channel profiles (subscribers, @handle, visit channel) from video items from channels
        val hasVideoMetrics = descLower.contains("views") || descLower.contains("ago") ||
                descLower.contains("minute") || descLower.contains("hour") || descLower.contains("second")
        val isChannel = (lower.contains("subscribers") || descLower.contains("subscribers") ||
                lower.contains("सदस्य") || descLower.contains("सदस्य") ||
                lower.startsWith("@") || descLower.startsWith("@") ||
                lower.contains("visit channel") || descLower.contains("visit channel") ||
                viewIdLower.contains("channel_avatar") || viewIdLower.contains("channel_header") ||
                viewIdLower.contains("channel_name")) && !hasVideoMetrics
        if (isChannel) {
            return VideoCandidateType.CHANNEL
        }

        // 4. Playlists / Mixes
        val isPlaylist = lower.contains("playlist") || lower.contains("प्लेलिस्ट") || lower.contains("mix -") ||
                lower.contains("mixes •") || lower.contains("50+ videos") || lower.contains("10+ videos") ||
                descLower.contains("playlist") || viewIdLower.contains("playlist")
        if (isPlaylist) {
            return VideoCandidateType.PLAYLIST
        }

        // 5. Default to Video
        return VideoCandidateType.VIDEO
    }

    /**
     * Calculates deterministic relevance score between the candidate and search query.
     */
    fun computeRelevanceScore(
        title: String,
        rawText: String,
        query: String,
        type: VideoCandidateType
    ): Pair<Float, String?> {
        val titleClean = normalizeForMatching(title)
        val rawClean = normalizeForMatching(rawText)
        val queryClean = normalizeForMatching(query)

        // Immediate rejection for non-video types
        when (type) {
            VideoCandidateType.UNKNOWN -> return Pair(0.0f, "अस्वीकृत: गैर-वीडियो तत्व (UI Control)")
            VideoCandidateType.AD -> return Pair(0.0f, "अस्वीकृत: स्पॉन्सर्ड विज्ञापन (Ad)")
            VideoCandidateType.SHORT -> return Pair(0.15f, "अस्वीकृत: YouTube Shorts वीडियो")
            VideoCandidateType.CHANNEL -> return Pair(0.10f, "अस्वीकृत: चैनल प्रोफाइल (Channel)")
            VideoCandidateType.PLAYLIST -> return Pair(0.20f, "अस्वीकृत: प्लेलिस्ट (Playlist)")
            else -> {}
        }

        val queryTokens = extractSignificantTokens(queryClean)
        val titleTokens = extractSignificantTokens(titleClean)
        val rawTokens = extractSignificantTokens(rawClean)

        if (queryTokens.isEmpty()) {
            return Pair(0.50f, null)
        }

        var matchPoints = 0.0f
        var totalKeyWeights = 0.0f

        // Token matches
        for (qToken in queryTokens) {
            val weight = if (qToken.length > 3) 2.0f else 1.0f
            totalKeyWeights += weight

            val matchedInTitle = titleTokens.any { it == qToken || it.contains(qToken) || qToken.contains(it) }
            val matchedInRaw = rawTokens.any { it == qToken || it.contains(qToken) || qToken.contains(it) }

            if (matchedInTitle) {
                matchPoints += weight
            } else if (matchedInRaw) {
                matchPoints += (weight * 0.7f)
            }
        }

        var baseScore = if (totalKeyWeights > 0f) (matchPoints / totalKeyWeights) else 0.0f

        // Bonus if full cleaned query is an exact substring in title
        if (titleClean.contains(queryClean) && queryClean.length > 3) {
            baseScore += 0.25f
        }

        // Cap to 1.0f
        val finalScore = baseScore.coerceIn(0.0f, 1.0f)

        return if (finalScore < MIN_RELEVANCE_THRESHOLD) {
            Pair(finalScore, "अस्वीकृत: कम प्रासंगिकता (${(finalScore * 100).toInt()}%)")
        } else {
            Pair(finalScore, null)
        }
    }

    private fun calculateCandidateConfidence(score: Float, type: VideoCandidateType, element: UIElement): Float {
        if (type != VideoCandidateType.VIDEO) return 0.1f
        var conf = score

        // Bonus if clickable
        if (element.isClickable) {
            conf += 0.10f
        }

        // Bonus if rich content description with duration/channel
        val desc = element.contentDescription ?: ""
        if (desc.contains("views") || desc.contains("ago") || desc.contains("minute") || desc.contains("hour") || desc.contains("views")) {
            conf += 0.05f
        }

        return conf.coerceIn(0.0f, 1.0f)
    }

    /**
     * Strips residual YouTube metadata like "4 minutes, 20 seconds", "1.2M views", "3 weeks ago".
     */
    fun extractCleanTitle(rawText: String): String {
        var clean = rawText.trim()

        // YouTube accessibility content description format: "Title by ChannelName 1.2M views 2 years ago 4 minutes, 20 seconds"
        // Only strip " by " if it is followed by YouTube metrics (views/ago/time/subscribers) to avoid truncating titles like "Saved by the Bell", "Driven by Passion", "Created by AI"
        val byIndex = clean.indexOf(" by ", ignoreCase = true)
        if (byIndex > 2) {
            val afterBy = clean.substring(byIndex)
            val hasVideoMetrics = afterBy.contains("view", ignoreCase = true) ||
                    afterBy.contains("ago", ignoreCase = true) ||
                    afterBy.contains("minute", ignoreCase = true) ||
                    afterBy.contains("second", ignoreCase = true) ||
                    afterBy.contains("hour", ignoreCase = true) ||
                    clean.contains(" views", ignoreCase = true)
            if (hasVideoMetrics) {
                clean = clean.substring(0, byIndex).trim()
            }
        }

        // Remove duration indicators at end like " - 4:25" or "4:25"
        clean = clean.replace(Regex("""\s*[-–]\s*\d{1,2}:\d{2}(?::\d{2})?\s*$"""), "")
        clean = clean.replace(Regex("""\s*\d{1,2}:\d{2}(?::\d{2})?\s*$"""), "")

        // Remove newlines
        clean = clean.replace(Regex("""[\r\n]+"""), " ").trim()

        return clean
    }

    private fun normalizeForMatching(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun extractSignificantTokens(normalizedText: String): List<String> {
        return normalizedText.split(" ")
            .map { it.trim() }
            .filter { it.length >= 2 && !STOPWORDS.contains(it) }
    }

    private fun isStaticControlOrNav(rawText: String, element: UIElement): Boolean {
        // Exclude editable elements (e.g. search input field)
        if (element.isEditable || element.className?.contains("EditText", ignoreCase = true) == true) {
            return true
        }

        // Exclude elements with viewId indicating search inputs or clear buttons
        val viewIdLower = (element.viewId ?: "").lowercase(Locale.ROOT)
        if (viewIdLower.contains("search_edit_text") || viewIdLower.contains("search_src_text") ||
            viewIdLower.contains("search_clear") || viewIdLower.contains("clear_search") ||
            viewIdLower.contains("search_input") || viewIdLower.contains("action_bar_root")) {
            return true
        }

        val lower = rawText.lowercase(Locale.ROOT).trim()
        val staticItems = setOf(
            "search", "search youtube", "voice search", "home", "subscriptions", "you",
            "explore", "trending", "music", "gaming", "news", "films", "filters", "all",
            "shorts", "videos", "unwatched", "watched", "recently uploaded", "live",
            "back", "navigate up", "cast", "notifications", "menu", "more options",
            "clear search", "clear query",
            "सर्च", "यूट्यूब खोजें", "होम", "सदस्यताएं", "आप"
        )
        if (staticItems.contains(lower)) return true

        val descLower = (element.contentDescription ?: "").lowercase(Locale.ROOT).trim()
        if (staticItems.contains(descLower)) return true

        return false
    }
}
