package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.widget.Toast

class AppAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.e("JARVIS_PROOF", "🔥 SERVICE CONNECTED")

        Toast.makeText(
            applicationContext,
            "JARVIS SERVICE CONNECTED",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // DO NOT FILTER ANYTHING
        Log.e("JARVIS_PROOF", "⚡ EVENT RECEIVED")

        Toast.makeText(
            applicationContext,
            "EVENT RECEIVED",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onInterrupt() {
        Log.e("JARVIS_PROOF", "SERVICE INTERRUPTED")
    }
}
