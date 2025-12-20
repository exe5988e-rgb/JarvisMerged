package com.jarvismini.automation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator

class JarvisNotificationService : NotificationListenerService() {

    private val TAG = "JarvisNotifService"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName != "com.whatsapp") return

        val notification = sbn.notification
        val extras = notification.extras
        val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()

        if (messageText.isNullOrEmpty() || sender.isNullOrEmpty()) return

        Log.i(TAG, "Notification from $sender: $messageText")

        val input = AutoReplyInput(
            messageText = messageText,
            isFromOwner = false
        )

        val decision = AutoReplyOrchestrator.handle(input)

        when (decision) {
            is ReplyDecision.AutoReply -> {
                Log.i(TAG, "Jarvis AutoReply: ${decision.message}")
                // Open chat and send message
                val root = AccessibilityServiceHelper.getRootNode() // helper to get root node
                ChatOpener.openChat(AccessibilityServiceHelper.service, root)
                MessageSender.sendMessage(root, decision.message)
            }

            ReplyDecision.NoReply -> {
                Log.i(TAG, "Jarvis will not reply")
            }
        }

        // Auto-close chat
        val root = AccessibilityServiceHelper.getRootNode()
        AutoCloser.closeChat(root)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
