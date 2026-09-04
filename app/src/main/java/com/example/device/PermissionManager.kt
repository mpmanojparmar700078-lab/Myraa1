package com.example.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.services.MyraAccessibilityService

/**
 * Centralized Permission & Capability Manager for Myra.
 * Strictly adheres to Android runtime permissions, MediaProjection consent,
 * and AccessibilityService status without fake or simulated permissions.
 */
class PermissionManager(private val context: Context) {

    companion object {
        const val PERMISSION_MICROPHONE = Manifest.permission.RECORD_AUDIO
        val PERMISSION_NOTIFICATIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            ""
        }

        const val REASON_MICROPHONE =
            "Microphone access is required for voice interaction and wake-word listening. Myra only listens when explicitly enabled and never secretly records or uploads raw audio."

        const val REASON_SCREEN_CAPTURE =
            "Screen capture access (MediaProjection) enables visual screen understanding for UI navigation and game interaction. Consent must be granted directly through the Android system dialog."

        const val REASON_ACCESSIBILITY =
            "Accessibility Service allows Myra to semantically read UI elements, detect buttons and text fields, and perform verified navigation actions on your behalf."
    }

    /**
     * Checks if RECORD_AUDIO runtime permission is granted.
     */
    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if notification permission is granted (Android 13+).
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if MyraAccessibilityService is active and bound in Android Settings.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return MyraAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    /**
     * Checks if Screen Capture (MediaProjection) consent token is available.
     */
    fun hasScreenCaptureCapability(): Boolean {
        return ScreenCaptureManager.hasProjectionConsent()
    }

    /**
     * Returns an intent to launch the system Accessibility Settings screen.
     */
    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Returns an intent to launch Myra's App Info / Permissions settings screen.
     */
    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Returns an intent to launch App Notification Settings screen.
     */
    fun createNotificationSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            createAppSettingsIntent()
        }
    }
}
