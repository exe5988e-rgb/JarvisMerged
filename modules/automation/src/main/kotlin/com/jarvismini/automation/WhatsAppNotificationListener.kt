// ===== FILE: app/src/main/java/com/jarvismini/automation/WhatsAppNotificationListener.kt =====
package com.jarvismini.automation

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import java.util.concurrent.ConcurrentHashMap

class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "JARVIS-NOTIF"
        private const val WHATSAPP = "com.whatsapp"

        private const val NORMAL_COOLDOWN_MS = 30_000L
        private const val URGENT_COOLDOWN_MS = 5 * 60 * 1000L

        private val URGENT_REGEX = Regex(
            "\\b(urgent|emergency|asap|important|immediately|call\\s*me|call\\s*now)\\b",
            RegexOption.IGNORE_CASE
        )
    }

    private val handledKeys = ConcurrentHashMap<String, Boolean>()
    private val lastReplyTime = ConcurrentHashMap<String, Long>()
    private val lastUrgentReplyTime = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != WHATSAPP) return

        val notification = sbn.notification
        val extras = notification.extras

        // Ignore summary notifications
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val title = extras.getString(Notification.EXTRA_TITLE)
        val convo = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)

        val isGroup =
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
            (!title.isNullOrEmpty() && !convo.isNullOrEmpty() && title != convo)

        if (isGroup) return

        val replyAction = notification.actions
            ?.firstOrNull { it.remoteInputs != null }
            ?: return

        if (handledKeys.putIfAbsent(sbn.key, true) != null) return

        val chatId = convo ?: title ?: return
        val now = System.currentTimeMillis()

        val messageText =
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

        val isUrgent = URGENT_REGEX.containsMatchIn(messageText)

        if (isUrgent) {
            val last = lastUrgentReplyTime[chatId] ?: 0L
            if (now - last < URGENT_COOLDOWN_MS) return
            lastUrgentReplyTime[chatId] = now
        } else {
            val last = lastReplyTime[chatId] ?: 0L
            if (now - last < NORMAL_COOLDOWN_MS) return
            lastReplyTime[chatId] = now
        }

        // 🔗 ORCHESTRATOR (MODE-AWARE DECISION)
        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput(
                messageText = messageText,
                isFromOwner = false
            )
        )

        if (decision !is ReplyDecision.AutoReply) {
            Log.d(TAG, "Mode blocked auto-reply")
            return
        }

        sendReply(replyAction, decision.message)
    }

    private fun sendReply(action: Notification.Action, replyText: String) {
        val replyIntent = Intent()
        val bundle = Bundle()

        for (input in action.remoteInputs) {
            bundle.putCharSequence(input.resultKey, replyText)
        }

        RemoteInput.addResultsToIntent(
            action.remoteInputs,
            replyIntent,
            bundle
        )

        try {
            action.actionIntent.send(this, 0, replyIntent)
            Log.d(TAG, "Jarvis auto-reply sent")
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Failed to send reply", e)
        }
    }
}
