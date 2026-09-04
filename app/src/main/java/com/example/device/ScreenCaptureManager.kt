package com.example.device

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages MediaProjection screen capture consent and observation lifecycle.
 * Strictly separates screen observation from UI interaction or touch injection.
 * Enforces privacy: zero unrequested uploads, stops capture when completed.
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"

        private var projectionConsentIntent: Intent? = null
        private var projectionResultCode: Int = Activity.RESULT_CANCELED
        private var activeMediaProjection: MediaProjection? = null

        private val _isScreenCaptureAvailable = MutableStateFlow(false)
        val isScreenCaptureAvailable: StateFlow<Boolean> = _isScreenCaptureAvailable.asStateFlow()

        private val _isObservingScreen = MutableStateFlow(false)
        val isObservingScreen: StateFlow<Boolean> = _isObservingScreen.asStateFlow()

        /**
         * Checks whether valid MediaProjection consent data is stored.
         */
        fun hasProjectionConsent(): Boolean {
            return projectionConsentIntent != null && projectionResultCode == Activity.RESULT_OK
        }

        /**
         * Saves the consent intent returned by the Android MediaProjection permission dialog.
         */
        fun setProjectionConsent(resultCode: Int, data: Intent?) {
            projectionResultCode = resultCode
            projectionConsentIntent = data
            _isScreenCaptureAvailable.value = (resultCode == Activity.RESULT_OK && data != null)
            Log.d(TAG, "Screen capture consent updated: available=${_isScreenCaptureAvailable.value}")
        }

        /**
         * Clears stored consent and terminates active projection.
         */
        fun revokeProjectionConsent() {
            try {
                activeMediaProjection?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping active media projection", e)
            }
            activeMediaProjection = null
            projectionConsentIntent = null
            projectionResultCode = Activity.RESULT_CANCELED
            _isScreenCaptureAvailable.value = false
            _isObservingScreen.value = false
            Log.d(TAG, "Screen capture consent revoked.")
        }
    }

    private val mediaProjectionManager: MediaProjectionManager? by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
    }

    /**
     * Creates the Android system consent intent for screen capture.
     */
    fun createScreenCaptureIntent(): Intent? {
        return mediaProjectionManager?.createScreenCaptureIntent()
    }

    /**
     * Obtains an active MediaProjection instance if consent was granted.
     */
    fun getMediaProjection(): MediaProjection? {
        val intent = projectionConsentIntent ?: return null
        if (activeMediaProjection == null && mediaProjectionManager != null) {
            try {
                activeMediaProjection = mediaProjectionManager?.getMediaProjection(projectionResultCode, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to instantiate MediaProjection", e)
            }
        }
        return activeMediaProjection
    }

    fun setObservingState(observing: Boolean) {
        _isObservingScreen.value = observing
    }
}
