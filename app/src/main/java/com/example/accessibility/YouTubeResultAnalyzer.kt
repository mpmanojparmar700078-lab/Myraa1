package com.example.accessibility

import com.example.models.ScreenNodeInfo
import com.example.models.ScreenSnapshot

class YouTubeResultAnalyzer {

    fun isYouTubeScreen(snapshot: ScreenSnapshot): Boolean {
        return snapshot.packageName?.contains("youtube") == true
    }

    fun findSearchIcon(nodes: List<ScreenNodeInfo>): ScreenNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            val id = node.resourceId?.lowercase() ?: ""
            desc.contains("search") || text.contains("search") || id.contains("search")
        }
    }

    fun findFirstVideoItem(nodes: List<ScreenNodeInfo>): ScreenNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription ?: ""
            node.isClickable && desc.contains("views", ignoreCase = true) ||
                    (desc.isNotBlank() && desc.contains("ago", ignoreCase = true))
        }
    }

    fun findPlayButton(nodes: List<ScreenNodeInfo>): ScreenNodeInfo? {
        return nodes.firstOrNull { node ->
            val desc = node.contentDescription?.lowercase() ?: ""
            val text = node.text?.lowercase() ?: ""
            desc.contains("play") || text.contains("play")
        }
    }
}
