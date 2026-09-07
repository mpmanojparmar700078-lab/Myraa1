package com.example.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyraAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyraAccessibility"

        @Volatile
        var activeService: MyraAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = activeService != null

        private val _currentPackage = MutableStateFlow<String?>("com.example")
        val currentPackage: StateFlow<String?> = _currentPackage.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        Log.d(TAG, "MyraAccessibilityService connected and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            _currentPackage.value = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "MyraAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeService == this) {
            activeService = null
        }
        Log.d(TAG, "MyraAccessibilityService destroyed")
    }
}
