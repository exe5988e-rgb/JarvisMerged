package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AppAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.e("JARVIS_PROOF", "🔥 SERVICE CONNECTED")
        Toast.makeText(
            this,
            "JARVIS ACCESSIBILITY CONNECTED",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.e("JARVIS_PROOF", "EVENT RECEIVED: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.e("JARVIS_PROOF", "SERVICE INTERRUPTED")
    }
}
