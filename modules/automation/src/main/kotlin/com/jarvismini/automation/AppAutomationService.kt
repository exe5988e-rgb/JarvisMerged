package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat

class AppAutomationService : AccessibilityService() {

    private val TAG = "JarvisMini"
    private val CHANNEL_ID = "jarvis_debug"

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.i(TAG, "🔥 ACCESSIBILITY SERVICE CONNECTED")

        // ⚠️ DO NOT override serviceInfo fields here
        createNotificationChannel()
        showDebugNotification("Service connected & alive")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        Log.d(
            TAG,
            "📡 EVENT: ${AccessibilityEvent.eventTypeToString(event.eventType)} | pkg=${event.packageName}"
        )

        // TEMP: accept ALL events for debugging
        val root = rootInActiveWindow ?: return

        // Proof-of-life notification
        showDebugNotification("Event from ${event.packageName}")

        // ⚠️ Disable auto-reply until detection confirmed
        // sendMessage(root, "Hello Mr Shams, Jarvis here.")
    }

    private fun sendMessage(root: AccessibilityNodeInfo, message: String) {
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

        Log.i(TAG, "✅ AUTO-REPLY SENT")
        showDebugNotification("Reply sent")
    }

    private fun showDebugNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Debug",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ SERVICE INTERRUPTED")
    }
}
