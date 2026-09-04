package com.example.game

import android.content.Context
import android.util.Log
import com.example.device.CapabilityManager
import com.example.services.MyraAccessibilityService
import com.example.vision.CoordinateMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GameInteractionResult(
    val success: Boolean,
    val target: String,
    val message: String,
    val verified: Boolean
)

/**
 * Game Visual Mode & Interaction Manager.
 * Orchestrates touch/gesture planning and execution for games.
 */
class GameInteractionManager(
    private val context: Context,
    private val capabilityManager: CapabilityManager,
    private val gestureManager: GestureManager = GestureManager(),
    private val coordinateMapper: CoordinateMapper = CoordinateMapper(context),
    private val targetDetector: GameTargetDetector = GameTargetDetector(coordinateMapper)
) {
    companion object {
        private const val TAG = "GameInteractionManager"
    }

    private val _isGameModeActive = MutableStateFlow(false)
    val isGameModeActive: StateFlow<Boolean> = _isGameModeActive.asStateFlow()

    fun setGameModeActive(active: Boolean) {
        _isGameModeActive.value = active
    }

    /**
     * Executes a targeted game action with verified gesture dispatch and screen state checking.
     */
    suspend fun executeGameAction(targetQuery: String): GameInteractionResult {
        if (!capabilityManager.capabilities.value.isGameInteractionEnabled) {
            return GameInteractionResult(
                success = false,
                target = targetQuery,
                message = "Game Interaction Mode is disabled in Myra Settings.",
                verified = false
            )
        }

        if (!gestureManager.canDispatchGestures()) {
            return GameInteractionResult(
                success = false,
                target = targetQuery,
                message = "Accessibility Service is required to dispatch game touch gestures. Please enable it in Android Settings.",
                verified = false
            )
        }

        val target = targetDetector.detectGameTarget(targetQuery)
            ?: return GameInteractionResult(
                success = false,
                target = targetQuery,
                message = "Could not identify high-confidence game target for '$targetQuery'.",
                verified = false
            )

        val pixel = coordinateMapper.normalizedToPixel(target.normalizedX, target.normalizedY)
        Log.d(TAG, "Dispatching game action for ${target.target} at pixel (${pixel.x}, ${pixel.y})")

        val stateBefore = MyraAccessibilityService.getInstance()?.captureCurrentScreenState()

        val gestureResult = gestureManager.performTap(pixel.x.toFloat(), pixel.y.toFloat())
        if (!gestureResult.success) {
            return GameInteractionResult(
                success = false,
                target = target.target,
                message = gestureResult.message,
                verified = false
            )
        }

        // Wait briefly for potential visual reaction
        delay(400L)
        val stateAfter = MyraAccessibilityService.getInstance()?.captureCurrentScreenState()
        val verified = stateBefore == null || stateAfter == null || stateBefore.hasChangedSignificantly(stateAfter)

        return GameInteractionResult(
            success = true,
            target = target.target,
            message = "Dispatched ${target.recommendedAction} on ${target.target} (${pixel.x}, ${pixel.y})",
            verified = verified
        )
    }
}
