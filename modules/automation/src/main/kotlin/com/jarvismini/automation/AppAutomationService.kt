package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AppAutomationService : AccessibilityService() {

    private val TAG = "JARVIS"
    private val WHATSAPP = "com.whatsapp"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(this, "Jarvis connected", Toast.LENGTH_SHORT).show()
        Log.e(TAG, "SERVICE CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 🔒 PHASE 2: Notification only
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        if (event.packageName?.toString() != WHATSAPP) return

        val data = event.parcelableData
        if (data !is Notification) return

        val intent = data.contentIntent ?: return

        try {
            intent.send()
            Toast.makeText(
                this,
                "WhatsApp notification opened",
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "NOTIFICATION CLICKED")
        } catch (e: Exception) {
            Log.e(TAG, "FAILED TO OPEN NOTIFICATION", e)
        }
    }

    override fun onInterrupt() {
        Log.e(TAG, "SERVICE INTERRUPTED")
    }
}
