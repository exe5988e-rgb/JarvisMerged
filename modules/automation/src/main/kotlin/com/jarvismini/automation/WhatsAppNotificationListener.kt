// ===== FILE: app/src/main/java/com/jarvismini/automation/WhatsAppNotificationListener.kt =====
package com.jarvismini.automation

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.RemoteInput
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

    /** Prevent duplicate notification handling */
    private val handledKeys = ConcurrentHashMap.newKeySet<String>()

    /** Per-chat cooldown */
    private val lastReplyTime = ConcurrentHashMap<String, Long>()

    /** Separate urgent cooldown */
    private val lastUrgentReplyTime = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != WHATSAPP) return

        val notification = sbn.notification
        val extras = notification.extras

        // 1️⃣ Ignore summary notifications
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // 2️⃣ Ignore GROUP chats (hard rule)
        val isGroupFlag =
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        val title = extras.getString(Notification.EXTRA_TITLE)
        val convo = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)

        val isGroupHeuristic =
            !title.isNullOrEmpty() &&
            !convo.isNullOrEmpty() &&
            title != convo

        if (isGroupFlag || isGroupHeuristic) {
            Log.d(TAG, "Group chat detected — skipping reply")
            return
        }

        // 3️⃣ Find reply action with RemoteInput
        val replyAction = notification.actions
            ?.firstOrNull { it.remoteInputs != null }
            ?: return

        // 4️⃣ Deduplicate notification
        if (!handledKeys.add(sbn.key)) return

        // 5️⃣ Identify chat
        val chatId = convo ?: title ?: return
        val now = System.currentTimeMillis()

        // 6️⃣ Extract message text
        val messageText =
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

        val isUrgent = URGENT_REGEX.containsMatchIn(messageText)

        // 7️⃣ Cooldown handling
        if (isUrgent) {
            val lastUrgent = lastUrgentReplyTime[chatId] ?: 0L
            if (now - lastUrgent < URGENT_COOLDOWN_MS) return
            lastUrgentReplyTime[chatId] = now
        } else {
            val lastNormal = lastReplyTime[chatId] ?: 0L
            if (now - lastNormal < NORMAL_COOLDOWN_MS) return
            lastReplyTime[chatId] = now
        }

        // 8️⃣ Choose reply text
        val replyText = if (isUrgent) urgentReply() else normalReply()

        // 9️⃣ Send reply
        sendReply(replyAction, replyText)
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

    // =========================
    // ✨ REPLY CONTENT
    // =========================

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
