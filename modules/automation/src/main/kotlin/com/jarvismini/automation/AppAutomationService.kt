package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class AppAutomationService : AccessibilityService() {

    private val WHATSAPP = "com.whatsapp"
    private var lastMessage: String? = null

    override fun onServiceConnected() {
        Toast.makeText(
            this,
            "Jarvis Accessibility Connected",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != WHATSAPP) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return

        val message = findRealIncomingMessage(root) ?: return
        if (message == lastMessage) return

        lastMessage = message

        Toast.makeText(
            this,
            "📩 WhatsApp message received",
            Toast.LENGTH_SHORT
        ).show()

        // Close chat ONLY after real message
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun findRealIncomingMessage(node: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            val text = n.text?.toString() ?: ""
            if (text.length < 4) continue

            // 🚫 Ignore UI labels
            if (
                text.equals("Message", true) ||
                text.equals("Video call", true) ||
                text.equals("Voice call", true)
            ) continue

            // 🚫 Ignore buttons & toolbar items
            if (n.isClickable || n.isFocusable) continue
            if (!n.contentDescription.isNullOrEmpty()) continue

            // ✅ Likely real message bubble
            return text

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    override fun onInterrupt() {}
}
