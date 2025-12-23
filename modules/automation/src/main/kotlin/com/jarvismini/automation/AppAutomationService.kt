package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.widget.Toast

class AppAutomationService : AccessibilityService() {

    private val TARGET_PACKAGE = "com.whatsapp"

    override fun onServiceConnected() {
        Log.e("JARVIS_PROOF", "🔥 SERVICE CONNECTED")

        Toast.makeText(
            applicationContext,
            "JARVIS CONNECTED",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg != TARGET_PACKAGE) return

        Log.e("JARVIS_PROOF", "📲 WHATSAPP EVENT")

        Toast.makeText(
            applicationContext,
            "WHATSAPP EVENT",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onInterrupt() {
        Log.e("JARVIS_PROOF", "SERVICE INTERRUPTED")
    }
}
