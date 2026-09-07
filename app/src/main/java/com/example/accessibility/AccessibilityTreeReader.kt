package com.example.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.models.ScreenNodeInfo
import com.example.models.ScreenSnapshot

class AccessibilityTreeReader {

    companion object {
        private const val MAX_DEPTH = 15
        private const val MAX_NODES = 250
    }

    fun captureSnapshot(rootNode: AccessibilityNodeInfo?): ScreenSnapshot {
        if (rootNode == null) {
            return ScreenSnapshot()
        }

        val packageName = rootNode.packageName?.toString()
        val visibleNodes = mutableListOf<ScreenNodeInfo>()
        traverseAndCollect(rootNode, visibleNodes, 0)

        return ScreenSnapshot(
            packageName = packageName,
            visibleNodes = visibleNodes
        )
    }

    private fun traverseAndCollect(node: AccessibilityNodeInfo, list: MutableList<ScreenNodeInfo>, depth: Int) {
        if (depth > MAX_DEPTH || list.size >= MAX_NODES) return

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val resId = node.viewIdResourceName
        val className = node.className?.toString()

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable) {
            list.add(
                ScreenNodeInfo(
                    text = text,
                    contentDescription = desc,
                    resourceId = resId,
                    className = className,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isVisibleToUser = node.isVisibleToUser,
                    boundsRect = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndCollect(child, list, depth + 1)
        }
    }

    fun findNodesByText(rootNode: AccessibilityNodeInfo?, query: String): List<AccessibilityNodeInfo> {
        if (rootNode == null || query.isBlank()) return emptyList()
        return rootNode.findAccessibilityNodeInfosByText(query) ?: emptyList()
    }

    fun findNodesByViewId(rootNode: AccessibilityNodeInfo?, viewId: String): List<AccessibilityNodeInfo> {
        if (rootNode == null || viewId.isBlank()) return emptyList()
        return rootNode.findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()
    }

    fun findFirstClickableNode(rootNode: AccessibilityNodeInfo?, targetText: String): AccessibilityNodeInfo? {
        if (rootNode == null) return null
        val lowerTarget = targetText.lowercase()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (text.contains(lowerTarget) || desc.contains(lowerTarget)) {
                if (node.isClickable) return node
                // Check clickable parent
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) return parent
                    parent = parent.parent
                }
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun findFirstEditableNode(rootNode: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (rootNode == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable) return node

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }
}
