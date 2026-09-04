package com.example.automation

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.game.GestureManager
import com.example.platform.android.AppLauncher
import com.example.services.MyraAccessibilityService
import com.example.vision.CoordinateMapper
import kotlinx.coroutines.delay

data class ActionExecutionOutput(
    val success: Boolean,
    val message: String,
    val actionType: PlannedActionType
)

/**
 * Executes planned actions using legitimate Android APIs:
 * - Accessibility Node Actions for semantic UI controls.
 * - AccessibilityService dispatchGesture for coordinates/games.
 * - AppLauncher for launching targeted applications.
 */
class ActionExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val gestureManager: GestureManager = GestureManager(),
    private val coordinateMapper: CoordinateMapper = CoordinateMapper(context)
) {
    companion object {
        private const val TAG = "ActionExecutor"
    }

    /**
     * Dispatches the planned action.
     */
    suspend fun execute(action: PlannedAction): ActionExecutionOutput {
        val service = MyraAccessibilityService.getInstance()

        return when (action.type) {
            PlannedActionType.OPEN_APP -> {
                val launchResult = appLauncher.launchPackage(action.target)
                ActionExecutionOutput(
                    success = launchResult.success,
                    message = launchResult.message,
                    actionType = action.type
                )
            }

            PlannedActionType.TAP -> {
                if (action.targetElement != null && service != null) {
                    val label = action.targetElement.text ?: action.targetElement.contentDescription ?: ""
                    val clickResult = service.clickElementByText(label, exact = false)
                    ActionExecutionOutput(
                        success = clickResult.success,
                        message = clickResult.message,
                        actionType = action.type
                    )
                } else if (action.normX != null && action.normY != null) {
                    val px = coordinateMapper.normalizedToPixel(action.normX, action.normY)
                    val res = gestureManager.performTap(px.x.toFloat(), px.y.toFloat())
                    ActionExecutionOutput(
                        success = res.success,
                        message = res.message,
                        actionType = action.type
                    )
                } else {
                    ActionExecutionOutput(
                        success = false,
                        message = "No target element or coordinates available for Tap.",
                        actionType = action.type
                    )
                }
            }

            PlannedActionType.TYPE_TEXT -> {
                if (service == null) {
                    return ActionExecutionOutput(false, "Accessibility Service is not active.", action.type)
                }
                val text = action.textToType ?: ""
                val root = service.rootInActiveWindow
                val editableNode = findEditableNode(root)

                if (editableNode != null) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    }
                    val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    ActionExecutionOutput(
                        success = success,
                        message = if (success) "Entered text '$text'" else "Failed to set text in editable field",
                        actionType = action.type
                    )
                } else {
                    ActionExecutionOutput(
                        success = false,
                        message = "Could not find active editable input field on screen.",
                        actionType = action.type
                    )
                }
            }

            PlannedActionType.BACK -> {
                if (service == null) {
                    return ActionExecutionOutput(false, "Accessibility Service not running.", action.type)
                }
                val res = service.performGlobalBack()
                ActionExecutionOutput(res.success, res.message, action.type)
            }

            PlannedActionType.SCROLL -> {
                if (service == null) {
                    return ActionExecutionOutput(false, "Accessibility Service not running.", action.type)
                }
                val res = service.performScrollForward()
                ActionExecutionOutput(res.success, res.message, action.type)
            }

            PlannedActionType.SWIPE -> {
                val dims = coordinateMapper.getScreenDimensions()
                val res = gestureManager.performSwipe(
                    dims.widthPx * 0.5f,
                    dims.heightPx * 0.7f,
                    dims.widthPx * 0.5f,
                    dims.heightPx * 0.3f
                )
                ActionExecutionOutput(res.success, res.message, action.type)
            }

            PlannedActionType.WAIT -> {
                delay(800L)
                ActionExecutionOutput(true, "Waited for layout stabilization.", action.type)
            }

            PlannedActionType.LONG_PRESS -> {
                if (action.normX != null && action.normY != null) {
                    val px = coordinateMapper.normalizedToPixel(action.normX, action.normY)
                    val res = gestureManager.performLongPress(px.x.toFloat(), px.y.toFloat())
                    ActionExecutionOutput(res.success, res.message, action.type)
                } else {
                    ActionExecutionOutput(false, "No coordinates for LongPress.", action.type)
                }
            }

            PlannedActionType.CUSTOM_TOUCH -> {
                val normX = action.normX ?: 500
                val normY = action.normY ?: 850
                val px = coordinateMapper.normalizedToPixel(normX, normY)
                val res = gestureManager.performTap(px.x.toFloat(), px.y.toFloat())
                ActionExecutionOutput(res.success, res.message, action.type)
            }
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findEditableNode(child)
            if (found != null) return found
        }
        return null
    }
}
