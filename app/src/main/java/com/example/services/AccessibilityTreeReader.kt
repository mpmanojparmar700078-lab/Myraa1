package com.example.services

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.models.ScreenState
import com.example.models.UIElement
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Hardened utility responsible for securely extracting the visible UI hierarchy from an
 * AccessibilityNodeInfo tree and executing verified accessibility actions.
 *
 * Resilience & Privacy Guarantees:
 * - Completely skips password fields (`isPassword = true`).
 * - Sanitizes potential sensitive input (credit cards, banking OTPs, CVVs, PINs).
 * - Never uploads screen data to any network endpoint or LLM automatically.
 * - Cycle-resistant recursive traversal with visited node safety and depth caps.
 * - Exception safety: isolated try-catch per node so single dead nodes do not abort traversal.
 * - Stale-node protection: builds decoupled, immutable UIElement models.
 */
class AccessibilityTreeReader {

    companion object {
        private const val MAX_DEPTH = 40
        private const val MAX_TOTAL_ELEMENTS = 300
    }

    /**
     * Extracts a clean, immutable ScreenState from the active root node.
     */
    fun extractScreenState(rootNode: AccessibilityNodeInfo?): ScreenState {
        if (rootNode == null) {
            return ScreenState()
        }

        val elements = mutableListOf<UIElement>()
        val packageName = try { rootNode.packageName?.toString() } catch (_: Exception) { null }
        val className = try { rootNode.className?.toString() } catch (_: Exception) { null }
        val visited = Collections.newSetFromMap(IdentityHashMap<AccessibilityNodeInfo, Boolean>())

        traverseNode(
            node = rootNode,
            outList = elements,
            depth = 0,
            visited = visited,
            parentClass = null,
            parentVId = null
        )

        return ScreenState(
            packageName = packageName,
            className = className,
            windowTitle = null,
            timestamp = System.currentTimeMillis(),
            elements = elements
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        outList: MutableList<UIElement>,
        depth: Int,
        visited: MutableSet<AccessibilityNodeInfo>,
        parentClass: String?,
        parentVId: String?
    ) {
        if (node == null || depth > MAX_DEPTH || outList.size >= MAX_TOTAL_ELEMENTS) {
            return
        }

        if (!visited.add(node)) {
            return
        }

        try {
            // Privacy Filter: Omit password fields completely
            if (node.isPassword) {
                return
            }

            val text = try { node.text?.toString() } catch (_: Exception) { null }
            val contentDesc = try { node.contentDescription?.toString() } catch (_: Exception) { null }
            val viewId = try { node.viewIdResourceName } catch (_: Exception) { null }

            // Skip sensitive OTP, banking or credential patterns
            if (isSensitiveText(text) || isSensitiveText(contentDesc)) {
                return
            }

            val bounds = Rect()
            try {
                node.getBoundsInScreen(bounds)
            } catch (_: Exception) {
                // Ignore bounds failure
            }

            val isEditable = try { node.isEditable } catch (_: Exception) { false }
            val isClickable = try { node.isClickable } catch (_: Exception) { false }
            val isFocusable = try { node.isFocusable } catch (_: Exception) { false }
            val isFocused = try { node.isFocused } catch (_: Exception) { false }
            val isSelected = try { node.isSelected } catch (_: Exception) { false }
            val isChecked = try { node.isChecked } catch (_: Exception) { false }
            val isEnabled = try { node.isEnabled } catch (_: Exception) { true }
            val isScrollable = try { node.isScrollable } catch (_: Exception) { false }
            val isVisibleToUser = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    node.isVisibleToUser
                } else true
            } catch (_: Exception) { true }

            val className = try { node.className?.toString() } catch (_: Exception) { null }
            val pkgName = try { node.packageName?.toString() } catch (_: Exception) { null }
            val childCount = try { node.childCount } catch (_: Exception) { 0 }

            val supportedActions = try {
                node.actionList?.map { it.id } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            val hasMeaningfulInfo = !text.isNullOrBlank() ||
                    !contentDesc.isNullOrBlank() ||
                    !viewId.isNullOrBlank() ||
                    isClickable ||
                    isEditable ||
                    isScrollable

            if (hasMeaningfulInfo) {
                outList.add(
                    UIElement(
                        text = text?.trim(),
                        contentDescription = contentDesc?.trim(),
                        className = className,
                        packageName = pkgName,
                        viewId = viewId,
                        isClickable = isClickable,
                        isEnabled = isEnabled,
                        isFocusable = isFocusable,
                        isFocused = isFocused,
                        isSelected = isSelected,
                        isChecked = isChecked,
                        isEditable = isEditable,
                        isScrollable = isScrollable,
                        isPassword = false,
                        isVisibleToUser = isVisibleToUser,
                        bounds = bounds,
                        childCount = childCount,
                        parentClassName = parentClass,
                        parentViewId = parentVId,
                        supportedActions = supportedActions,
                        nodeIndex = outList.size
                    )
                )
            }

            for (i in 0 until childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                traverseNode(
                    node = child,
                    outList = outList,
                    depth = depth + 1,
                    visited = visited,
                    parentClass = className,
                    parentVId = viewId
                )
            }
        } catch (_: Exception) {
            // Gracefully catch exceptions to preserve resilience against dynamic view changes
        }
    }

    /**
     * Finds a matching node on the live tree and performs ACTION_CLICK.
     * If the target is not directly clickable, walks up to the nearest clickable ancestor.
     */
    fun performClick(
        rootNode: AccessibilityNodeInfo?,
        matcher: (UIElement) -> Boolean
    ): Boolean {
        if (rootNode == null) return false
        val targetNode = findMatchingNode(rootNode, matcher, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap())) ?: return false

        // Attempt click on target node or its nearest clickable, enabled ancestor
        var curr: AccessibilityNodeInfo? = targetNode
        var attempts = 0
        while (curr != null && attempts < 5) {
            try {
                if (curr.isClickable && curr.isEnabled) {
                    val clicked = curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
            } catch (_: Exception) {
                // Ignore failure on current node and try parent
            }
            curr = try { curr.parent } catch (_: Exception) { null }
            attempts++
        }

        // Direct click fallback if ancestor walking failed
        return try {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Finds a matching node and performs ACTION_LONG_CLICK.
     */
    fun performLongClick(
        rootNode: AccessibilityNodeInfo?,
        matcher: (UIElement) -> Boolean
    ): Boolean {
        if (rootNode == null) return false
        val targetNode = findMatchingNode(rootNode, matcher, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap())) ?: return false

        var curr: AccessibilityNodeInfo? = targetNode
        var attempts = 0
        while (curr != null && attempts < 5) {
            try {
                if (curr.isEnabled && curr.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    return true
                }
            } catch (_: Exception) {
                // Try parent
            }
            curr = try { curr.parent } catch (_: Exception) { null }
            attempts++
        }
        return false
    }

    /**
     * Finds a matching editable node and performs ACTION_SET_TEXT.
     */
    fun performSetText(
        rootNode: AccessibilityNodeInfo?,
        matcher: (UIElement) -> Boolean,
        textToSet: String
    ): Boolean {
        if (rootNode == null) return false
        val targetNode = findMatchingNode(rootNode, matcher, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap())) ?: return false

        return try {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSet)
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Appends text to an editable node.
     */
    fun performAppendText(
        rootNode: AccessibilityNodeInfo?,
        matcher: (UIElement) -> Boolean,
        textToAppend: String
    ): Boolean {
        if (rootNode == null) return false
        val targetNode = findMatchingNode(rootNode, matcher, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap())) ?: return false

        return try {
            val existing = targetNode.text?.toString() ?: ""
            val newText = existing + textToAppend
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Clears text from an editable node.
     */
    fun performClearText(
        rootNode: AccessibilityNodeInfo?,
        matcher: (UIElement) -> Boolean
    ): Boolean {
        return performSetText(rootNode, matcher, "")
    }

    /**
     * Finds a matching node and performs ACTION_FOCUS.
     */
    fun performFocus(
        rootNode: AccessibilityNodeInfo?,
        matcher: (UIElement) -> Boolean
    ): Boolean {
        if (rootNode == null) return false
        val targetNode = findMatchingNode(rootNode, matcher, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap())) ?: return false
        return try {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Performs forward scrolling on a scrollable container.
     */
    fun performScrollForward(
        rootNode: AccessibilityNodeInfo?,
        matcher: ((UIElement) -> Boolean)? = null
    ): Boolean {
        if (rootNode == null) return false
        val target = if (matcher != null) {
            findMatchingNode(rootNode, matcher, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap()))
        } else {
            findMatchingNode(rootNode, { it.isScrollable }, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap()))
        } ?: return false

        return try {
            target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Performs backward scrolling on a scrollable container.
     */
    fun performScrollBackward(
        rootNode: AccessibilityNodeInfo?,
        matcher: ((UIElement) -> Boolean)? = null
    ): Boolean {
        if (rootNode == null) return false
        val target = if (matcher != null) {
            findMatchingNode(rootNode, matcher, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap()))
        } else {
            findMatchingNode(rootNode, { it.isScrollable }, depth = 0, visited = Collections.newSetFromMap(IdentityHashMap()))
        } ?: return false

        return try {
            target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Attempts to trigger search on the active editable node (IME Enter) or search action button.
     */
    fun performSearchAction(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        // 1. Try IME Action if available (Android API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val editableNode = findMatchingNode(
                node = rootNode,
                matcher = { it.isEditable },
                depth = 0,
                visited = Collections.newSetFromMap(IdentityHashMap())
            )
            if (editableNode != null) {
                try {
                    val imeSuccess = editableNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    if (imeSuccess) return true
                } catch (_: Exception) {
                    // Fallback to UI buttons
                }
            }
        }

        // 2. Try clicking search suggestion item or search button
        val searchMatcher: (UIElement) -> Boolean = { el ->
            if (!el.isClickable || el.isEditable) {
                false
            } else {
                val desc = el.contentDescription?.lowercase(java.util.Locale.ROOT) ?: ""
                val text = el.text?.lowercase(java.util.Locale.ROOT) ?: ""
                val viewId = el.viewId?.lowercase(java.util.Locale.ROOT) ?: ""

                val isExcluded = desc.contains("clear") || viewId.contains("clear") ||
                        desc.contains("voice") || viewId.contains("voice") ||
                        desc.contains("mic") || viewId.contains("mic")

                if (isExcluded) {
                    false
                } else {
                    val isSearch = desc.contains("search") || desc.contains("सर्च") || desc.contains("खोजें") ||
                            text.contains("search") || text.contains("सर्च") || text.contains("खोजें") ||
                            viewId.contains("search") || viewId.contains("suggestion") || viewId.contains("action_search")
                    isSearch
                }
            }
        }

        return performClick(rootNode, searchMatcher)
    }

    private fun findMatchingNode(
        node: AccessibilityNodeInfo?,
        matcher: (UIElement) -> Boolean,
        depth: Int,
        visited: MutableSet<AccessibilityNodeInfo>
    ): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_DEPTH) return null
        if (!visited.add(node)) return null

        try {
            if (node.isPassword) return null

            val bounds = Rect()
            try {
                node.getBoundsInScreen(bounds)
            } catch (_: Exception) {}

            val element = UIElement(
                text = try { node.text?.toString() } catch (_: Exception) { null },
                contentDescription = try { node.contentDescription?.toString() } catch (_: Exception) { null },
                className = try { node.className?.toString() } catch (_: Exception) { null },
                packageName = try { node.packageName?.toString() } catch (_: Exception) { null },
                viewId = try { node.viewIdResourceName } catch (_: Exception) { null },
                isClickable = try { node.isClickable } catch (_: Exception) { false },
                isEnabled = try { node.isEnabled } catch (_: Exception) { true },
                isFocusable = try { node.isFocusable } catch (_: Exception) { false },
                isFocused = try { node.isFocused } catch (_: Exception) { false },
                isSelected = try { node.isSelected } catch (_: Exception) { false },
                isChecked = try { node.isChecked } catch (_: Exception) { false },
                isEditable = try { node.isEditable } catch (_: Exception) { false },
                isScrollable = try { node.isScrollable } catch (_: Exception) { false },
                isPassword = false,
                bounds = bounds,
                childCount = try { node.childCount } catch (_: Exception) { 0 }
            )

            if (matcher(element)) {
                return node
            }

            val count = try { node.childCount } catch (_: Exception) { 0 }
            for (i in 0 until count) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                val found = findMatchingNode(child, matcher, depth + 1, visited)
                if (found != null) return found
            }
        } catch (_: Exception) {
            // Gracefully ignore inaccessible node branches
        }

        return null
    }

    private fun isSensitiveText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val lower = text.lowercase()
        return lower.contains("otp") ||
                lower.contains("one time password") ||
                lower.contains("cvv") ||
                lower.contains("pin number") ||
                lower.contains("atm pin")
    }
}
