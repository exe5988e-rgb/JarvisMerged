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
import com.jarvismini.automation.decision.ReplyDecision

class AppAutomationService : AccessibilityService() {

    private val TAG = "JarvisMini-AutoService"
    private val CHANNEL_ID = "jarvis_debug"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Jarvis Accessibility Service CONNECTED")

        serviceInfo = serviceInfo.apply {
            eventTypes =
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return

        // 🔔 DEBUG NOTIFICATION (PROOF OF LIFE)
        showDebugNotification("Jarvis detected screen update")

        // 🔥 FORCED AUTO-REPLY (TEMP TEST MODE)
        val reply = "Hello Mr Shams, Jarvis here. I received your message."

        sendMessage(root, reply)
    }

    private fun sendMessage(root: AccessibilityNodeInfo, message: String) {
        val inputField = NodeFinder.findInputField(root)
        val sendButton = NodeFinder.findSendButton(root)

        if (inputField == null || sendButton == null) {
            Log.w(TAG, "Input or Send button not found")
            return
        }

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            message
        )

        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        Log.i(TAG, "Jarvis auto-reply SENT")
        showDebugNotification("Jarvis sent auto-reply")
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
        Log.i(TAG, "Jarvis Accessibility INTERRUPTED")
    }
}