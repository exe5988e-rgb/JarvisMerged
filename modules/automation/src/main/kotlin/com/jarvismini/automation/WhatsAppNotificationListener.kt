// ===== FILE: app/src/main/java/com/jarvismini/automation/WhatsAppNotificationListener.kt =====
package com.jarvismini.automation

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput   // ✅ SYSTEM RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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

        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val title = extras.getString(Notification.EXTRA_TITLE)
        val convo = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)

        val isGroup =
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
            (!title.isNullOrEmpty() && !convo.isNullOrEmpty() && title != convo)

        if (isGroup) return

        // ✅ HARD SAFE ACTION PICK
        val replyAction = notification.actions
            ?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
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

        val replyText = if (isUrgent) urgentReply() else normalReply()
        sendReply(replyAction, replyText)
    }

    // ✅ FIXED: SAFE, SYSTEM RemoteInput ONLY
    private fun sendReply(action: Notification.Action, replyText: String) {

        val inputs = action.remoteInputs ?: return
        if (inputs.isEmpty()) return

        val intent = Intent()
        val bundle = Bundle()

        for (input in inputs) {
            bundle.putCharSequence(input.resultKey, replyText)
        }

        RemoteInput.addResultsToIntent(inputs, intent, bundle)

        try {
            action.actionIntent.send(this, 0, intent)
            Log.d(TAG, "Jarvis auto-reply sent")
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Reply failed", e)
        }
    }

    private fun normalReply(): String =
        """
        Hello 👋
        This is Jarvis, Aamir sir’s assistant.

        Aamir is currently unavailable.
        I’ll make sure your message is seen.
        """.trimIndent()

    private fun urgentReply(): String =
        """
        Hello 👋
        This is Jarvis, Aamir sir’s assistant.

        This message looks urgent.
        Please call Aamir directly.
        """.trimIndent()
}
