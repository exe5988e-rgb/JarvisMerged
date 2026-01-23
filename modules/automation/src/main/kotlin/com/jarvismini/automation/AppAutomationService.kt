package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AppAutomationService : AccessibilityService() {

    private val TAG = "JARVIS"
    private val WHATSAPP = "com.whatsapp"
    private val ONEPLUS_CLOCK = "com.oneplus.deskclock"

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHelper.init(this)
        Toast.makeText(this, "Jarvis connected", Toast.LENGTH_SHORT).show()
        Log.e(TAG, "SERVICE CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // ⏱ OnePlus Clock automation
        if (
            event.packageName?.toString() == ONEPLUS_CLOCK &&
            (
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            )
        ) {
            AccessibilityServiceHelper.handleOnePlusClock()
            return
        }

        // 💬 WhatsApp notification logic (unchanged)
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        if (event.packageName?.toString() != WHATSAPP) return

        val data = event.parcelableData
        if (data !is Notification) return

        val launchIntent = packageManager.getLaunchIntentForPackage(WHATSAPP)
            ?: return

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)

        Toast.makeText(this, "WhatsApp opened from notification", Toast.LENGTH_SHORT).show()
        Log.e(TAG, "WHATSAPP LAUNCHED")
    }

    override fun onInterrupt() {
        Log.e(TAG, "SERVICE INTERRUPTED")
    }
}
