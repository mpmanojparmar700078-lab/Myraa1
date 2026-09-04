package com.example.game

import android.graphics.Rect
import com.example.vision.CoordinateMapper

data class GameTarget(
    val target: String,
    val normalizedX: Int,
    val normalizedY: Int,
    val confidence: Float,
    val recommendedAction: String,
    val boundingBox: Rect = Rect()
) {
    val isConfident: Boolean
        get() = confidence >= 0.70f
}

/**
 * Detects visual game controls and targets for games running custom renderers
 * (OpenGL/Vulkan/Unity) where standard accessibility nodes do not exist.
 */
class GameTargetDetector(private val coordinateMapper: CoordinateMapper) {

    companion object {
        // Standard game HUD relative layouts (normalized 0..1000 scale)
        private val COMMON_GAME_TARGETS = mapOf(
            "play" to Pair(500, 850),          // Bottom-center Play / Start
            "start" to Pair(500, 850),
            "resume" to Pair(500, 750),
            "attack" to Pair(880, 820),        // Bottom-right attack / fire
            "jump" to Pair(920, 700),          // Bottom-right jump
            "menu" to Pair(80, 80),            // Top-left menu / pause
            "pause" to Pair(920, 80),          // Top-right pause
            "settings" to Pair(920, 80),
            "close" to Pair(920, 100),         // Top-right close dialog
            "claim" to Pair(500, 800)          // Bottom-center claim reward
        )
    }

    /**
     * Resolves target coordinates for a requested game action.
     */
    fun detectGameTarget(query: String): GameTarget? {
        val clean = query.trim().lowercase()
        for ((key, coords) in COMMON_GAME_TARGETS) {
            if (clean.contains(key)) {
                val point = coordinateMapper.normalizedToPixel(coords.first, coords.second)
                return GameTarget(
                    target = key.replaceFirstChar { it.uppercase() },
                    normalizedX = coords.first,
                    normalizedY = coords.second,
                    confidence = 0.88f,
                    recommendedAction = "TAP",
                    boundingBox = Rect(point.x - 40, point.y - 40, point.x + 40, point.y + 40)
                )
            }
        }
        return null
    }
}
