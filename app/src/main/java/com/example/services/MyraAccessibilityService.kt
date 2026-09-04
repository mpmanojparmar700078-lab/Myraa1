package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.example.models.AccessibilityActionResult
import com.example.models.AccessibilityActionType
import com.example.models.AccessibilityDebugLog
import com.example.models.ScreenState
import com.example.models.UIElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Accessibility Service for Myra V3.1 Foundation.
 *
 * Responsibilities:
 * - Listens for window state and content changes to detect active foreground packages.
 * - Extracts visible UI hierarchy on-demand using AccessibilityTreeReader.
 * - Provides official Accessibility Action Foundation (CLICK, SET_TEXT, FOCUS, BACK).
 * - Exposes reactive state for UI & Developer Inspection mode.
 * - Enforces privacy: skips passwords, sensitive OTPs, and never uploads screen data.
 */
class MyraAccessibilityService : AccessibilityService() {

    private val treeReader = AccessibilityTreeReader()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "MyraAccessibility"

        private var instance: MyraAccessibilityService? = null

        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

        private val _currentScreenState = MutableStateFlow<ScreenState?>(null)
        val currentScreenState: StateFlow<ScreenState?> = _currentScreenState.asStateFlow()

        private val _currentActivePackage = MutableStateFlow<String?>(null)
        val currentActivePackage: StateFlow<String?> = _currentActivePackage.asStateFlow()

        private val _debugLogs = MutableStateFlow<List<AccessibilityDebugLog>>(emptyList())
        val debugLogs: StateFlow<List<AccessibilityDebugLog>> = _debugLogs.asStateFlow()

        private val _latestYouTubeSelection = MutableStateFlow<com.example.models.YouTubeResultSelection?>(null)
        val latestYouTubeSelection: StateFlow<com.example.models.YouTubeResultSelection?> = _latestYouTubeSelection.asStateFlow()

        fun setLatestYouTubeSelection(selection: com.example.models.YouTubeResultSelection?) {
            _latestYouTubeSelection.value = selection
        }

        fun logAction(appName: String, actionName: String, target: String, result: String) {
            val log = AccessibilityDebugLog(
                timestamp = System.currentTimeMillis(),
                appName = appName,
                actionName = actionName,
                target = target,
                result = result
            )
            val current = _debugLogs.value.toMutableList()
            if (current.size >= 50) {
                current.removeAt(0)
            }
            current.add(log)
            _debugLogs.value = current
            Log.d(TAG, "[$appName] $actionName -> $target: $result")
        }

        fun clearDebugLogs() {
            _debugLogs.value = emptyList()
        }

        /**
         * Checks if the Accessibility Service is enabled in Android System Settings.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (_isServiceEnabled.value) return true
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            val expectedServiceName = "${context.packageName}/${MyraAccessibilityService::class.java.canonicalName}"
            return enabledServices.any { serviceInfo ->
                serviceInfo.id.equals(expectedServiceName, ignoreCase = true) ||
                        serviceInfo.resolveInfo?.serviceInfo?.packageName == context.packageName
            }
        }

        /**
         * Opens the Android System Accessibility Settings for the user.
         */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            }
        }

        /**
         * Returns active instance if running.
         */
        fun getInstance(): MyraAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        Log.i(TAG, "Myra Accessibility Service connected successfully.")
        captureCurrentScreenState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            _currentActivePackage.value = pkg
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Avoid querying window state when our own app is focused to avoid IME/Compose conflicts
            if (pkg != null && pkg != packageName) {
                serviceScope.launch {
                    try {
                        val state = captureCurrentScreenState()
                        _currentScreenState.value = state
                    } catch (e: Exception) {
                        Log.w(TAG, "Error capturing screen state: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Myra Accessibility Service interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceEnabled.value = false
        _currentScreenState.value = null
        _currentActivePackage.value = null
        Log.i(TAG, "Myra Accessibility Service unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _isServiceEnabled.value = false
        super.onDestroy()
    }

    /**
     * Captures and parses the active window accessibility tree.
     */
    fun captureCurrentScreenState(): ScreenState {
        val root = rootInActiveWindow
        val state = treeReader.extractScreenState(root)
        val activePkg = _currentActivePackage.value ?: state.packageName
        val finalState = if (state.packageName == null && activePkg != null) {
            state.copy(packageName = activePkg)
        } else {
            state
        }
        _currentScreenState.value = finalState
        return finalState
    }

    /**
     * Executes CLICK on a matching UI element with coordinate gesture fallback if node action fails.
     */
    fun clickElement(
        matcher: (UIElement) -> Boolean,
        allowGestureFallback: Boolean = true
    ): AccessibilityActionResult {
        val root = rootInActiveWindow
        if (root != null) {
            val success = treeReader.performClick(root, matcher)
            if (success) {
                return AccessibilityActionResult(
                    success = true,
                    actionType = AccessibilityActionType.CLICK,
                    message = "Click action performed successfully via accessibility node"
                )
            }
        }

        // Gesture Tap Fallback: if node action failed but element exists with valid on-screen bounds
        if (allowGestureFallback) {
            val state = captureCurrentScreenState()
            val matchedEl = state.elements.firstOrNull(matcher)
            if (matchedEl != null && matchedEl.bounds.width() > 0 && matchedEl.bounds.height() > 0) {
                val cx = matchedEl.bounds.centerX().toFloat()
                val cy = matchedEl.bounds.centerY().toFloat()
                val gestureDispatched = dispatchTapGesture(cx, cy)
                if (gestureDispatched) {
                    return AccessibilityActionResult(
                        success = true,
                        actionType = AccessibilityActionType.GESTURE_TAP,
                        message = "Click action performed successfully via gesture tap at (${cx.toInt()}, ${cy.toInt()})"
                    )
                }
            }
        }

        return AccessibilityActionResult(
            success = false,
            actionType = AccessibilityActionType.CLICK,
            message = "Element not found or not clickable"
        )
    }

    /**
     * Executes SET_TEXT on a matching editable UI element.
     */
    fun setTextOnElement(matcher: (UIElement) -> Boolean, textToSet: String): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.SET_TEXT,
                message = "No active window available to set text"
            )

        val success = treeReader.performSetText(root, matcher, textToSet)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.SET_TEXT,
            message = if (success) "Text set successfully" else "Editable element not found"
        )
    }

    /**
     * Executes FOCUS on a matching UI element.
     */
    fun focusElement(matcher: (UIElement) -> Boolean): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.FOCUS,
                message = "No active window available to focus"
            )

        val success = treeReader.performFocus(root, matcher)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.FOCUS,
            message = if (success) "Focus action performed successfully" else "Element not focusable"
        )
    }

    /**
     * Executes LONG CLICK on a matching UI element.
     */
    fun longClickElement(matcher: (UIElement) -> Boolean): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.LONG_CLICK,
                message = "No active window available to long click"
            )

        val success = treeReader.performLongClick(root, matcher)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.LONG_CLICK,
            message = if (success) "Long click performed successfully" else "Element not found or not long-clickable"
        )
    }

    /**
     * Appends text to an editable UI element.
     */
    fun appendTextOnElement(matcher: (UIElement) -> Boolean, textToAppend: String): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.APPEND_TEXT,
                message = "No active window available to append text"
            )

        val success = treeReader.performAppendText(root, matcher, textToAppend)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.APPEND_TEXT,
            message = if (success) "Text appended successfully" else "Editable element not found"
        )
    }

    /**
     * Clears text on an editable UI element.
     */
    fun clearTextOnElement(matcher: (UIElement) -> Boolean): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.CLEAR_TEXT,
                message = "No active window available to clear text"
            )

        val success = treeReader.performClearText(root, matcher)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.CLEAR_TEXT,
            message = if (success) "Text cleared successfully" else "Editable element not found"
        )
    }

    /**
     * Performs forward scrolling on a scrollable container.
     */
    fun scrollForward(matcher: ((UIElement) -> Boolean)? = null): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.SCROLL_FORWARD,
                message = "No active window available to scroll"
            )

        val success = treeReader.performScrollForward(root, matcher)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.SCROLL_FORWARD,
            message = if (success) "Scrolled forward successfully" else "Scrollable container not found or scroll failed"
        )
    }

    /**
     * Performs backward scrolling on a scrollable container.
     */
    fun scrollBackward(matcher: ((UIElement) -> Boolean)? = null): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.SCROLL_BACKWARD,
                message = "No active window available to scroll"
            )

        val success = treeReader.performScrollBackward(root, matcher)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.SCROLL_BACKWARD,
            message = if (success) "Scrolled backward successfully" else "Scrollable container not found or scroll failed"
        )
    }

    /**
     * Executes official global BACK action.
     */
    fun performGlobalBack(): AccessibilityActionResult {
        val success = performGlobalAction(GLOBAL_ACTION_BACK)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.BACK,
            message = if (success) "Global Back executed" else "Global Back action failed"
        )
    }

    /**
     * Executes official global HOME action.
     */
    fun performGlobalHome(): AccessibilityActionResult {
        val success = performGlobalAction(GLOBAL_ACTION_HOME)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.HOME,
            message = if (success) "Global Home executed" else "Global Home action failed"
        )
    }

    /**
     * Executes official global RECENTS / OVERVIEW action.
     */
    fun performGlobalRecents(): AccessibilityActionResult {
        val success = performGlobalAction(GLOBAL_ACTION_RECENTS)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.RECENTS,
            message = if (success) "Global Recents executed" else "Global Recents action failed"
        )
    }

    /**
     * Executes official global NOTIFICATIONS action.
     */
    fun performGlobalNotifications(): AccessibilityActionResult {
        val success = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.NOTIFICATIONS,
            message = if (success) "Global Notifications executed" else "Global Notifications action failed"
        )
    }

    /**
     * Attempts to trigger search using IME action or search button.
     */
    fun triggerSearch(): Boolean {
        val root = rootInActiveWindow ?: return false
        return treeReader.performSearchAction(root)
    }

    /**
     * Extracts all visible text from the current screen, combining labels,
     * content descriptions, and view texts into a clean reading representation.
     */
    fun getAllVisibleScreenText(): String {
        val state = captureCurrentScreenState()
        val builder = StringBuilder()
        state.packageName?.let { builder.append("App: $it\n") }

        for (el in state.elements) {
            val label = el.displayLabel
            if (label.isNotBlank()) {
                val clickableTag = if (el.isClickable) " [Clickable]" else ""
                val editableTag = if (el.isEditable) " [Input Field]" else ""
                builder.append("• $label$clickableTag$editableTag\n")
            }
        }
        return builder.toString().trim()
    }

    /**
     * Finds and clicks an element matching target text or content description.
     */
    fun clickElementByText(targetText: String, exact: Boolean = false): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.CLICK,
                message = "No active window available"
            )

        val matcher: (UIElement) -> Boolean = { el ->
            val t = el.text ?: ""
            val cd = el.contentDescription ?: ""
            if (exact) {
                t.equals(targetText, ignoreCase = true) || cd.equals(targetText, ignoreCase = true)
            } else {
                t.contains(targetText, ignoreCase = true) || cd.contains(targetText, ignoreCase = true)
            }
        }

        val success = treeReader.performClick(root, matcher)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.CLICK,
            message = if (success) "Clicked on '$targetText'" else "Element with text '$targetText' not found or not clickable"
        )
    }

    /**
     * Finds and clicks an element matching a view resource ID.
     */
    fun clickElementByViewId(viewId: String): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.CLICK,
                message = "No active window available"
            )

        val matcher: (UIElement) -> Boolean = { el ->
            el.viewId?.contains(viewId, ignoreCase = true) == true
        }

        val success = treeReader.performClick(root, matcher)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.CLICK,
            message = if (success) "Clicked view ID '$viewId'" else "Element with view ID '$viewId' not found"
        )
    }

    /**
     * Dispatches a gesture tap at the specified screen coordinates (X, Y).
     */
    fun dispatchTapGesture(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Gesture tap at ($x, $y) completed successfully")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture tap at ($x, $y) was cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a swipe / scroll gesture across the screen.
     */
    fun dispatchScrollGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Scroll gesture completed from ($startX, $startY) to ($endX, $endY)")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Scroll gesture cancelled")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Performs forward scrolling on the active scrollable window.
     */
    fun performScrollForward(): AccessibilityActionResult {
        val root = rootInActiveWindow
            ?: return AccessibilityActionResult(
                success = false,
                actionType = AccessibilityActionType.SCROLL_FORWARD,
                message = "No active window available"
            )
        val success = treeReader.performScrollForward(root)
        return AccessibilityActionResult(
            success = success,
            actionType = AccessibilityActionType.SCROLL_FORWARD,
            message = if (success) "Scrolled forward" else "Scroll forward failed or window not scrollable"
        )
    }
}
