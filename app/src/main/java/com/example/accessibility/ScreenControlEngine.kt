package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.models.ActionResult
import com.example.models.ActionStep
import com.example.models.ActionStepResult
import com.example.services.MyraAccessibilityService
import kotlinx.coroutines.delay

class ScreenControlEngine(
    private val treeReader: AccessibilityTreeReader = AccessibilityTreeReader(),
    private val elementMatcher: ElementMatcher = ElementMatcher()
) {

    val isAccessibilityActive: Boolean
        get() = MyraAccessibilityService.isRunning

    suspend fun executeStep(step: ActionStep): ActionStepResult {
        val startTime = System.currentTimeMillis()
        val service = MyraAccessibilityService.activeService

        if (service == null) {
            return ActionStepResult(
                stepId = step.stepId,
                actionType = step.actionType,
                success = false,
                durationMs = System.currentTimeMillis() - startTime,
                error = "Accessibility service is not active. Enable it in system settings."
            )
        }

        val rootNode = service.rootInActiveWindow
        val actionTypeUpper = step.actionType.uppercase()

        val success: Boolean
        var error: String? = null
        var verifiedState: String? = null

        when (actionTypeUpper) {
            "CLICK" -> {
                val target = step.target ?: ""
                val matched = elementMatcher.findBestMatch(rootNode, target)
                if (matched != null) {
                    val clicked = matched.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    success = clicked
                    if (!clicked) error = "Click action returned false on node (${matched.matchedField})"
                    else verifiedState = "Clicked element '$target' via ${matched.matchedField}"
                } else {
                    success = false
                    error = "Could not find element matching '$target'"
                }
            }

            "TYPE", "TYPE_TEXT" -> {
                val textToType = step.value ?: step.target ?: ""
                val editable = treeReader.findFirstEditableNode(rootNode)
                if (editable != null) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
                    }
                    val typed = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    success = typed
                    if (!typed) error = "Failed to set text in editable node"
                    else verifiedState = "Typed '$textToType'"
                } else {
                    success = false
                    error = "No editable text field found on screen"
                }
            }

            "SCROLL", "SCROLL_DOWN", "SCROLL_FORWARD" -> {
                val scrollNode = findScrollableNode(rootNode)
                if (scrollNode != null) {
                    success = scrollNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    if (!success) error = "Scroll forward returned false"
                    else verifiedState = "Scrolled forward"
                } else {
                    success = false
                    error = "No scrollable container found"
                }
            }

            "SCROLL_UP", "SCROLL_BACKWARD" -> {
                val scrollNode = findScrollableNode(rootNode)
                if (scrollNode != null) {
                    success = scrollNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    if (!success) error = "Scroll backward returned false"
                    else verifiedState = "Scrolled backward"
                } else {
                    success = false
                    error = "No scrollable container found"
                }
            }

            "BACK", "GO_BACK" -> {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                verifiedState = "Navigated back"
            }

            "HOME", "GO_HOME" -> {
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                verifiedState = "Navigated home"
            }

            "WAIT" -> {
                delay(step.waitMs)
                success = true
                verifiedState = "Waited ${step.waitMs}ms"
            }

            else -> {
                success = false
                error = "Unsupported accessibility action: $actionTypeUpper"
            }
        }

        if (step.waitMs > 0 && actionTypeUpper != "WAIT") {
            delay(step.waitMs)
        }

        return ActionStepResult(
            stepId = step.stepId,
            actionType = step.actionType,
            success = success,
            durationMs = System.currentTimeMillis() - startTime,
            error = error,
            verifiedState = verifiedState
        )
    }

    private fun findScrollableNode(rootNode: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (rootNode == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) return node

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    fun getCurrentSnapshot(): com.example.models.ScreenSnapshot {
        val service = MyraAccessibilityService.activeService ?: return com.example.models.ScreenSnapshot()
        return treeReader.captureSnapshot(service.rootInActiveWindow)
    }

    suspend fun waitForPackage(targetPackage: String, timeoutMs: Long = 2000L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val snapshot = getCurrentSnapshot()
            if (snapshot.packageName?.contains(targetPackage, ignoreCase = true) == true) {
                return true
            }
            delay(250L)
        }
        return false
    }

    suspend fun waitForCondition(
        timeoutMs: Long = 2500L,
        intervalMs: Long = 300L,
        condition: (com.example.models.ScreenSnapshot) -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val snapshot = getCurrentSnapshot()
            if (condition(snapshot)) {
                return true
            }
            delay(intervalMs)
        }
        return false
    }

    fun clickElementByTarget(target: String): Boolean {
        val service = MyraAccessibilityService.activeService ?: return false
        val rootNode = service.rootInActiveWindow ?: return false
        val matched = elementMatcher.findBestMatch(rootNode, target) ?: return false
        return matched.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
