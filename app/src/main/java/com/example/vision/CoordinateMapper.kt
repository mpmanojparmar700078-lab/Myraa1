package com.example.vision

import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

data class ScreenDimensions(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val isLandscape: Boolean
)

/**
 * Maps normalized coordinates (0..1000 or relative fractions) to actual physical screen pixel bounds.
 * Accounts for device resolution, aspect ratio, orientation, and insets.
 */
class CoordinateMapper(private val context: Context) {

    /**
     * Resolves the current physical screen dimensions.
     */
    fun getScreenDimensions(): ScreenDimensions {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = wm.currentWindowMetrics
            val bounds = windowMetrics.bounds
            ScreenDimensions(
                widthPx = bounds.width(),
                heightPx = bounds.height(),
                densityDpi = context.resources.configuration.densityDpi,
                isLandscape = isLandscape
            )
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            ScreenDimensions(
                widthPx = dm.widthPixels,
                heightPx = dm.heightPixels,
                densityDpi = dm.densityDpi,
                isLandscape = isLandscape
            )
        }
    }

    /**
     * Converts a normalized coordinate (0..1000 scale) into absolute screen pixels.
     */
    fun normalizedToPixel(normX: Int, normY: Int): Point {
        val dims = getScreenDimensions()
        val clampedX = normX.coerceIn(0, 1000)
        val clampedY = normY.coerceIn(0, 1000)

        val px = (clampedX.toFloat() / 1000f * dims.widthPx).toInt()
        val py = (clampedY.toFloat() / 1000f * dims.heightPx).toInt()

        return Point(px, py)
    }

    /**
     * Converts relative float fractions (0.0..1.0) into absolute screen pixels.
     */
    fun fractionToPixel(fracX: Float, fracY: Float): Point {
        val dims = getScreenDimensions()
        val px = (fracX.coerceIn(0f, 1f) * dims.widthPx).toInt()
        val py = (fracY.coerceIn(0f, 1f) * dims.heightPx).toInt()
        return Point(px, py)
    }

    /**
     * Converts absolute pixel bounds into normalized coordinates (0..1000).
     */
    fun pixelToNormalized(pixelRect: Rect): Rect {
        val dims = getScreenDimensions()
        if (dims.widthPx <= 0 || dims.heightPx <= 0) return pixelRect

        val left = ((pixelRect.left.toFloat() / dims.widthPx) * 1000).toInt().coerceIn(0, 1000)
        val top = ((pixelRect.top.toFloat() / dims.heightPx) * 1000).toInt().coerceIn(0, 1000)
        val right = ((pixelRect.right.toFloat() / dims.widthPx) * 1000).toInt().coerceIn(0, 1000)
        val bottom = ((pixelRect.bottom.toFloat() / dims.heightPx) * 1000).toInt().coerceIn(0, 1000)

        return Rect(left, top, right, bottom)
    }
}
