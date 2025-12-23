package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

        if (event.packageName?.toString() != WHATSAPP) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return

        val messageNode = findLatestMessage(root) ?: return

        Toast.makeText(
            this,
            "New WhatsApp message detected",
            Toast.LENGTH_SHORT
        ).show()

        Log.e(TAG, "REAL MESSAGE DETECTED")

        // 🔓 AUTO OPEN CHAT (ONLY ONCE)
        messageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findLatestMessage(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (
                node.className == "android.widget.TextView" &&
                !node.text.isNullOrBlank() &&
                node.text.length < 300
            ) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    override fun onInterrupt() {
        Log.e(TAG, "SERVICE INTERRUPTED")
    }
}
