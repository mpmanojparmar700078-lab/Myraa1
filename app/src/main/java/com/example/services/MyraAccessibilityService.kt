package com.example.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MyraAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Listens to accessibility events for UI automation when requested
    }

    override fun onInterrupt() {
        // Handle accessibility service interruption
    }
}
