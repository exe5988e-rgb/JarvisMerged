// ===== FILE: app/src/main/java/com/jarvismini/automation/WhatsAppNotificationListener.kt =====
package com.jarvismini.automation

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.automation.decision.ReplyDecision
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
    private val handledKeys = ConcurrentHashMap<String, Boolean>()

    /** Per-chat cooldown */
    private val lastReplyTime = ConcurrentHashMap<String, Long>()

    /** Separate urgent cooldown */
    private val lastUrgentReplyTime = ConcurrentHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != WHATSAPP) return

        // 🔒 MODE GUARD (hard stop)
        if (!ModeGuard.allowsReply()) return

        val notification = sbn.notification
        val extras = notification.extras

        // 1️⃣ Ignore summary notifications
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // 2️⃣ Ignore group chats
        val isGroupFlag =
            extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        val title = extras.getString(Notification.EXTRA_TITLE)
        val convo = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)

        val isGroupHeuristic =
            !title.isNullOrEmpty() &&
            !convo.isNullOrEmpty() &&
            title != convo

        if (isGroupFlag || isGroupHeuristic) {
            Log.d(TAG, "Group chat detected — skipping")
            return
        }

        // 3️⃣ Find reply action
        val replyAction = notification.actions
            ?.firstOrNull { it.remoteInputs != null }
            ?: return

        // 4️⃣ Deduplicate notification
        if (handledKeys.putIfAbsent(sbn.key, true) != null) return

        // 5️⃣ Identify chat
        val chatId = convo ?: title ?: return
        val now = System.currentTimeMillis()

        // 6️⃣ Extract message text
        val messageText =
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

        val isUrgent = URGENT_REGEX.containsMatchIn(messageText)

        // 7️⃣ Cooldown
        if (isUrgent) {
            val lastUrgent = lastUrgentReplyTime[chatId] ?: 0L
            if (now - lastUrgent < URGENT_COOLDOWN_MS) return
            lastUrgentReplyTime[chatId] = now
        } else {
            val lastNormal = lastReplyTime[chatId] ?: 0L
            if (now - lastNormal < NORMAL_COOLDOWN_MS) return
            lastReplyTime[chatId] = now
        }

        // 8️⃣ ORCHESTRATOR DECISION (🔥 THIS WAS MISSING 🔥)
        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput(
                messageText = messageText,
                isFromOwner = false
            )
        )

        when (decision) {
            is ReplyDecision.AutoReply ->
                sendReply(replyAction, decision.message)

            ReplyDecision.NoReply ->
                return
        }
    }

    private fun sendReply(action: Notification.Action, replyText: String) {
    val replyIntent = Intent()
    val bundle = Bundle()

    for (input in action.remoteInputs) {
        bundle.putCharSequence(input.resultKey, replyText)
    }

    android.app.RemoteInput.addResultsToIntent(
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
