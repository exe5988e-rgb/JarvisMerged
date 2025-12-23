package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppAutomationService : AccessibilityService() {

    private val TAG = "JarvisMini-AS"

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

            notificationTimeout = 50
        }

        Log.i(TAG, "✅ ACCESSIBILITY SERVICE CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        Log.d(
            TAG,
            "📡 EVENT=${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName}"
        )

        // TEMP: NO PACKAGE FILTER
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return

        val message = extractLatestMessage(root) ?: return

        Log.i(TAG, "📩 DETECTED MESSAGE: $message")
    }

    private fun extractLatestMessage(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var lastText: String? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.className == "android.widget.TextView") {
                val text = node.text?.toString()
                if (!text.isNullOrBlank() && text.length < 300) {
                    lastText = text
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return lastText
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ ACCESSIBILITY INTERRUPTED")
    }
}
