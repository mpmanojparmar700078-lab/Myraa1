package com.example.game

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import com.example.services.MyraAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class GestureResult(
    val success: Boolean,
    val gestureType: String,
    val message: String
)

/**
 * Manual Touch / Gesture Layer for games and canvas-based UIs where
 * Accessibility node hierarchy is absent (OpenGL, Vulkan, Unity, Unreal).
 * Uses legitimate Android AccessibilityService dispatchGesture.
 * Never fakes a touch event.
 */
class GestureManager {

    companion object {
        private const val TAG = "GestureManager"
    }

    /**
     * Checks if gesture injection is currently possible.
     */
    fun canDispatchGestures(): Boolean {
        return MyraAccessibilityService.getInstance() != null
    }

    /**
     * Performs a verified Tap gesture at screen coordinates (x, y).
     */
    suspend fun performTap(x: Float, y: Float, durationMs: Long = 100L): GestureResult {
        val service = MyraAccessibilityService.getInstance()
            ?: return GestureResult(
                success = false,
                gestureType = "TAP",
                message = "Accessibility Service is not active. Touch gestures require an enabled Accessibility Service."
            )

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchSuspend(service, gesture, "TAP", "Tap at ($x, $y)")
    }

    /**
     * Performs a verified Long Press gesture at screen coordinates (x, y).
     */
    suspend fun performLongPress(x: Float, y: Float, durationMs: Long = 600L): GestureResult {
        val service = MyraAccessibilityService.getInstance()
            ?: return GestureResult(
                success = false,
                gestureType = "LONG_PRESS",
                message = "Accessibility Service is not active."
            )

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchSuspend(service, gesture, "LONG_PRESS", "Long press at ($x, $y)")
    }

    /**
     * Performs a verified Swipe or Drag gesture from (startX, startY) to (endX, endY).
     */
    suspend fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 350L
    ): GestureResult {
        val service = MyraAccessibilityService.getInstance()
            ?: return GestureResult(
                success = false,
                gestureType = "SWIPE",
                message = "Accessibility Service is not active."
            )

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchSuspend(service, gesture, "SWIPE", "Swipe from ($startX, $startY) to ($endX, $endY)")
    }

    private suspend fun dispatchSuspend(
        service: AccessibilityService,
        gesture: GestureDescription,
        type: String,
        description: String
    ): GestureResult = suspendCancellableCoroutine { continuation ->
        try {
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "Gesture completed: $description")
                        if (continuation.isActive) {
                            continuation.resume(
                                GestureResult(
                                    success = true,
                                    gestureType = type,
                                    message = "$description completed successfully."
                                )
                            )
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Gesture cancelled: $description")
                        if (continuation.isActive) {
                            continuation.resume(
                                GestureResult(
                                    success = false,
                                    gestureType = type,
                                    message = "$description was cancelled by the system or target app."
                                )
                            )
                        }
                    }
                },
                null
            )

            if (!dispatched && continuation.isActive) {
                continuation.resume(
                    GestureResult(
                        success = false,
                        gestureType = type,
                        message = "System refused to dispatch gesture. App or screen state does not permit injection."
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching gesture", e)
            if (continuation.isActive) {
                continuation.resume(
                    GestureResult(
                        success = false,
                        gestureType = type,
                        message = "Failed to dispatch gesture: ${e.message}"
                    )
                )
            }
        }
    }
}
