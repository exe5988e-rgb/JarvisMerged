package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppAutomationService : AccessibilityService() {

    private val TAG = "JarvisMini"
    private val TARGET_PACKAGE = "com.whatsapp"
    private var lastSeenMessage: String? = null

    override fun onServiceConnected() {
        Log.i(TAG, "✅ Accessibility Service CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotificationEvent(event)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
        }
    }

    private fun handleNotificationEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != TARGET_PACKAGE) return

        val notificationText = event.text.joinToString(" ")
        Log.i(TAG, "📢 WhatsApp Notification: $notificationText")

        // Launch WhatsApp to read & reply
        val intent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        intent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != TARGET_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val message = extractLatestMessage(root) ?: return

        if (message == lastSeenMessage) return
        lastSeenMessage = message

        Log.i(TAG, "📩 NEW MESSAGE: $message")

        // Auto-reply
        sendReply(root, "Hello, Jarvis here!")
    }

    private fun extractLatestMessage(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var latestText: String? = null

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className == "android.widget.TextView") {
                val text = node.text?.toString()
                if (!text.isNullOrBlank() && text.length < 500) {
                    latestText = text
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return latestText
    }

    private fun sendReply(root: AccessibilityNodeInfo, message: String) {
        val inputField = NodeFinder.findInputField(root)
        val sendButton = NodeFinder.findSendButton(root)

        if (inputField == null || sendButton == null) {
            Log.w(TAG, "❌ Input or Send button not found")
            return
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                message
            )
        }

        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        Log.i(TAG, "✅ AUTO-REPLY SENT: $message")
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Accessibility Interrupted")
    }
}
