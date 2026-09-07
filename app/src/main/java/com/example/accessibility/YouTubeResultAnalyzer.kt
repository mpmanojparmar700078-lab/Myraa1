package com.example.accessibility

import com.example.models.ScreenNodeInfo
import com.example.models.ScreenSnapshot

class YouTubeResultAnalyzer {

    fun isYouTubeScreen(snapshot: ScreenSnapshot): Boolean {
        return snapshot.packageName?.contains("youtube", ignoreCase = true) == true
    }

    fun findSearchIcon(nodes: List<ScreenNodeInfo>): ScreenNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            val id = node.resourceId?.lowercase() ?: ""
            (desc.contains("search") || text.contains("search") || id.contains("search") ||
                    desc.contains("खोजें") || text.contains("खोजें")) && node.isClickable
        }
    }

    /**
     * Finds the most relevant video result matching the query using multi-signal semantic scoring.
     * Evaluates title, text, contentDescription, resourceId, duration markers, and filters out ads/channels.
     */
    fun findBestMatchingVideo(nodes: List<ScreenNodeInfo>, query: String): ScreenNodeInfo? {
        val queryLower = query.lowercase().trim()
        val queryTokens = queryLower.split(Regex("\\s+")).filter { it.length > 1 }

        val candidatesWithScores = nodes.mapNotNull { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            val resId = node.resourceId?.lowercase() ?: ""
            val fullText = "$desc $text"

            // Skip non-video UI elements
            if (isNonVideoElement(fullText, resId)) {
                return@mapNotNull null
            }

            var score = 0

            // 1. Exact query match
            if (desc.contains(queryLower) || text.contains(queryLower)) {
                score += 80
            }

            // 2. Token match
            for (token in queryTokens) {
                if (desc.contains(token) || text.contains(token)) {
                    score += 25
                }
            }

            // 3. Video metadata indicators (views, duration, upload date)
            if (desc.contains("views") || desc.contains("view") || desc.contains("व्यू")) score += 20
            if (desc.contains("ago") || desc.contains("पहले")) score += 15
            if (desc.contains("minute") || desc.contains("second") || desc.contains("hour") || desc.contains("मिनट")) score += 15
            if (Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(fullText)) score += 20

            // 4. Resource ID clues
            if (resId.contains("video_item") || resId.contains("compact_video_item") || resId.contains("thumbnail")) {
                score += 20
            }

            // Must be clickable or visible interactive node
            if (node.isClickable) score += 15

            // Penalize ads
            if (desc.contains("ad ·") || desc.contains("sponsored") || desc.contains("विज्ञापन")) {
                score -= 100
            }

            if (score >= 35) {
                Pair(node, score)
            } else {
                null
            }
        }

        return candidatesWithScores.maxByOrNull { it.second }?.first
    }

    fun findFirstVideoItem(nodes: List<ScreenNodeInfo>): ScreenNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            val fullText = "$desc $text"
            !isNonVideoElement(fullText, node.resourceId?.lowercase() ?: "") &&
                    (desc.contains("views") || desc.contains("ago") || Regex("\\b\\d{1,2}:\\d{2}\\b").containsMatchIn(fullText)) &&
                    node.isClickable
        }
    }

    /**
     * Verifies if the YouTube player screen is currently displayed.
     */
    fun isPlayerScreenVisible(nodes: List<ScreenNodeInfo>): Boolean {
        return nodes.any { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            val resId = node.resourceId?.lowercase() ?: ""
            resId.contains("watch_player") ||
                    resId.contains("player_fragment") ||
                    resId.contains("player_view") ||
                    desc.contains("player") ||
                    desc.contains("fullscreen") ||
                    desc.contains("pause video") ||
                    desc.contains("play video") ||
                    desc.contains("seek bar") ||
                    desc.contains("current time") ||
                    text.contains("re-center")
        }
    }

    /**
     * Verifies whether video playback has actively started.
     * In YouTube, an active playing video displays a "Pause video" control.
     */
    fun verifyPlaybackStarted(nodes: List<ScreenNodeInfo>): Boolean {
        return nodes.any { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            val resId = node.resourceId?.lowercase() ?: ""

            // Main playback indicator: presence of "pause" control button
            (desc.contains("pause video") || desc == "pause" || text == "pause" ||
                    resId.contains("pause_button") || desc.contains("पॉज़")) ||
                    // Or active playback progress / current playing indicator
                    (desc.contains("current time") && !desc.contains("0:00"))
        }
    }

    fun findPlayButton(nodes: List<ScreenNodeInfo>): ScreenNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            (desc.contains("play video") || desc == "play" || text == "play" || desc.contains("चलाएं")) && node.isClickable
        }
    }

    private fun isNonVideoElement(fullText: String, resId: String): Boolean {
        val nonVideoKeywords = listOf("subscribe", "subscribed", "filters", "search filters", "channel", "shorts", "explore", "subscriptions", "library")
        if (nonVideoKeywords.any { fullText == it || fullText.startsWith("$it ") }) return true
        if (resId.contains("channel_header") || resId.contains("filter_bar") || resId.contains("bottom_nav")) return true
        return false
    }
}
