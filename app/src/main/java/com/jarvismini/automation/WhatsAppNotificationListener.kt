package com.jarvismini.automation

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.core.AssistantProfile
import com.jarvismini.core.announce.AnnouncePriority
import com.jarvismini.core.announce.AnnouncementEngine
import com.jarvismini.core.events.EventLog
import com.jarvismini.core.events.EventType
import com.jarvismini.core.AutoReplyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "JARVIS-NOTIF"
        private const val WHATSAPP = "com.whatsapp"

        private const val NORMAL_COOLDOWN_MS = 30_000L
        private const val URGENT_COOLDOWN_MS = 5 * 60 * 1000L
        private const val AI_COOLDOWN_MS = 5_000L

        private val URGENT_REGEX = Regex(
            "\\b(urgent|emergency|asap|important|immediately|call\\s*me|call\\s*now)\\b",
            RegexOption.IGNORE_CASE
        )
    }

    private val announcedKeys = ConcurrentHashMap<String, Boolean>()
    private val handledKeys = ConcurrentHashMap<String, Boolean>()
    private val lastReplyTime = ConcurrentHashMap<String, Long>()
    private val lastUrgentReplyTime = ConcurrentHashMap<String, Long>()
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiSeen = ConcurrentHashMap<String, Boolean>()
    private val lastSentText = ConcurrentHashMap<String, String>()

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

        if (!isGroup && !title.isNullOrBlank()) {
            val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (announcedKeys.putIfAbsent(sbn.key + sbn.postTime, true) == null) {
                if (announcedKeys.size > 200) announcedKeys.clear()
                EventLog.add(EventType.MESSAGE, "Message from $title")
                com.jarvismini.core.learn.HabitMemory.recordMessageActivity()
                val urgent = URGENT_REGEX.containsMatchIn(body)
                AnnouncementEngine.announce(
                    (if (urgent) "Urgent message from $title, " else "New message from $title, ") +
                        AssistantProfile.addressUser() + ".",
                    if (urgent) AnnouncePriority.HIGH else AnnouncePriority.NORMAL,
                    "msg_$title"
                )
            }
        }

        if (isGroup) return

        val replyAction = notification.actions
            ?.firstOrNull { it.remoteInputs != null }
            ?: return

        val aiContact = AutoReplyProfile.isAiContact(title ?: convo)
        if (aiContact) {
            if (aiSeen.putIfAbsent(sbn.key + sbn.postTime, true) != null) return
            if (aiSeen.size > 200) aiSeen.clear()
        } else {
            if (handledKeys.putIfAbsent(sbn.key, true) != null) return
        }

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
            if (now - last < (if (aiContact) AI_COOLDOWN_MS else NORMAL_COOLDOWN_MS)) return
            lastReplyTime[chatId] = now
        }

        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput(
                messageText = messageText,
                isFromOwner = false
            )
        )

        if (decision !is ReplyDecision.AutoReply) {
            Log.d(TAG, "Jarvis mode blocks auto-reply")
            return
        }

        if (aiContact && messageText.isNotBlank() &&
            lastSentText[chatId]?.let { messageText.contains(it.take(30)) } != true
        ) {
            // Chat-enabled contact: AI-written reply, canned reply if the AI is unavailable.
            aiScope.launch {
                val locationReply = if (AutoReplyProfile.shareLocation() && LocationHelper.isLocationRequest(messageText)) {
                    try {
                        LocationHelper.shareText(applicationContext)?.let { com.jarvismini.core.AssistantVoice.withTag(it) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Location share failed: ${e.message}")
                        null
                    }
                } else null
                val reply = locationReply ?: try {
                    AiChatReplier.reply(applicationContext, chatId, title ?: chatId, messageText)
                } catch (e: Exception) {
                    Log.w(TAG, "AI reply failed: ${e.message}")
                    null
                } ?: decision.message
                lastSentText[chatId] = reply
                sendReply(replyAction, reply)
            }
            return
        }

        sendReply(replyAction, decision.message)
    }

    private fun sendReply(action: Notification.Action, replyText: String) {
        val fillInIntent = Intent()
        val results = Bundle()

        for (input in action.remoteInputs) {
            results.putCharSequence(input.resultKey, replyText)
        }

        RemoteInput.addResultsToIntent(
            action.remoteInputs,
            fillInIntent,
            results
        )

        try {
            action.actionIntent.send(
                this,
                0,
                fillInIntent
            )
            Log.d(TAG, "Jarvis auto-reply sent (stable)")
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Failed to send reply", e)
        }
    }
}
