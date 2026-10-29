package com.jarvismini.convo

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.jarvismini.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * WhatsApp-style notifications for Convo.
 *
 * One conversation notification per agent (MessagingStyle, newest messages stacked), a group
 * summary, inline Reply and Mark-as-read actions, and tap-to-open straight into the Convo tab.
 * Only messages addressed to the admin ([ADMIN_ID]) from someone else notify; agent-to-agent
 * traffic stays in the timeline. Nothing here ever reads or logs the admin token.
 */
object ConvoNotifier {

    const val CHANNEL_MESSAGES = "convo_messages"
    const val CHANNEL_SERVICE = "convo_service"
    const val GROUP_KEY = "convo_group"

    const val SERVICE_ID = 41_001
    private const val SUMMARY_ID = 41_000

    const val ACTION_OPEN = "com.jarvismini.convo.OPEN"
    const val ACTION_REPLY = "com.jarvismini.convo.REPLY"
    const val ACTION_MARK_READ = "com.jarvismini.convo.MARK_READ"
    const val EXTRA_AGENT = "convo_agent"
    const val KEY_REPLY = "convo_reply_text"

    /** True while the Convo screen is in the foreground; incoming messages are then already on screen. */
    @Volatile
    var screenVisible: Boolean = false

    /** Set when a notification tap asks MainScreen to jump to the Convo tab. */
    val openRequested = MutableStateFlow(false)

    private const val PREFS = "convo_notify_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_LAST_ID = "last_notified_id"
    private const val MAX_LINES = 8

    private class Line(val text: String, val at: Long, val mine: Boolean)

    private val lock = Any()
    private val history = LinkedHashMap<String, ArrayList<Line>>()
    private val names = HashMap<String, String>()

    // ── settings ─────────────────────────────────────────────────────────────

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasChoice(ctx: Context): Boolean = prefs(ctx).contains(K_ENABLED)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ENABLED, false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_ENABLED, on).apply()
        if (on) {
            ConvoNotificationService.start(ctx)
        } else {
            ConvoNotificationService.stop(ctx)
            clearAll(ctx)
        }
    }

    /** -1 means "never polled": the service then baselines to the newest message instead of notifying history. */
    fun lastNotifiedId(ctx: Context): Long = prefs(ctx).getLong(K_LAST_ID, -1L)

    fun setLastNotifiedId(ctx: Context, id: Long) {
        prefs(ctx).edit().putLong(K_LAST_ID, id).apply()
    }

    // ── deep link ────────────────────────────────────────────────────────────

    fun consumeOpenIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN) openRequested.value = true
    }

    // ── channels ─────────────────────────────────────────────────────────────

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Convo messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Messages from your agents"
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Convo connection", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps Convo listening for new messages"
                setShowBadge(false)
            },
        )
    }

    fun serviceNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Convo")
            .setContentText("Listening for agent messages")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent(ctx, null))
            .build()

    // ── incoming ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun showIncoming(ctx: Context, messages: List<ConvoMessage>, agents: List<ConvoAgent>) {
        val incoming = messages.filter { it.recipientId == ADMIN_ID && it.senderId != ADMIN_ID }
        if (incoming.isEmpty()) return
        val app = ctx.applicationContext
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        ensureChannels(app)

        val touched = LinkedHashSet<String>()
        synchronized(lock) {
            for (a in agents) names[a.id] = a.displayName.ifBlank { a.id }
            for (m in incoming.sortedBy { it.id }) {
                val lines = history.getOrPut(m.senderId) { ArrayList() }
                lines.add(Line(m.text, m.createdAt, mine = false))
                while (lines.size > MAX_LINES) lines.removeAt(0)
                touched.add(m.senderId)
            }
        }
        for (id in touched) postConversation(app, id, quiet = false, status = null)
        postSummary(app)
    }

    // ── reply / read ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun onReplySent(ctx: Context, agentId: String, text: String) {
        val app = ctx.applicationContext
        synchronized(lock) {
            val lines = history.getOrPut(agentId) { ArrayList() }
            lines.add(Line(text, System.currentTimeMillis(), mine = true))
            while (lines.size > MAX_LINES) lines.removeAt(0)
        }
        postConversation(app, agentId, quiet = true, status = null)
        postSummary(app)
    }

    @SuppressLint("MissingPermission")
    fun onReplyFailed(ctx: Context, agentId: String, reason: String?) {
        val app = ctx.applicationContext
        postConversation(app, agentId, quiet = true, status = "Reply failed: ${reason ?: "unknown error"}")
    }

    fun markRead(ctx: Context, agentId: String) {
        val app = ctx.applicationContext
        synchronized(lock) { history.remove(agentId) }
        NotificationManagerCompat.from(app).cancel(notificationId(agentId))
        val empty = synchronized(lock) { history.isEmpty() }
        if (empty) NotificationManagerCompat.from(app).cancel(SUMMARY_ID) else postSummary(app)
    }

    fun clearAll(ctx: Context) {
        val app = ctx.applicationContext
        val ids = synchronized(lock) {
            val keys = history.keys.toList()
            history.clear()
            keys
        }
        val nm = NotificationManagerCompat.from(app)
        for (id in ids) nm.cancel(notificationId(id))
        nm.cancel(SUMMARY_ID)
    }

    // ── building ─────────────────────────────────────────────────────────────

    private fun notificationId(agentId: String): Int = 42_000 + (agentId.hashCode() and 0xFFFF)

    private fun displayName(agentId: String): String = synchronized(lock) { names[agentId] } ?: agentId

    private fun openIntent(ctx: Context, agentId: String?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            action = ACTION_OPEN
            if (agentId != null) putExtra(EXTRA_AGENT, agentId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            ctx,
            if (agentId == null) 0 else notificationId(agentId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    private fun postConversation(ctx: Context, agentId: String, quiet: Boolean, status: String?) {
        val lines = synchronized(lock) { history[agentId]?.toList() } ?: return
        if (lines.isEmpty()) return
        val name = displayName(agentId)

        val me = Person.Builder().setName("You").build()
        val sender = Person.Builder().setName(name).setKey(agentId).build()
        val style = NotificationCompat.MessagingStyle(me)
        for (l in lines) style.addMessage(l.text, l.at, if (l.mine) null else sender)

        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val replyIntent = Intent(ctx, ConvoReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_AGENT, agentId)
        }
        val replyPi = PendingIntent.getBroadcast(
            ctx, notificationId(agentId), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPi,
        )
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply to $name").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(false)
            .build()

        val readIntent = Intent(ctx, ConvoReplyReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_AGENT, agentId)
        }
        val readPi = PendingIntent.getBroadcast(
            ctx, notificationId(agentId) + 100_000, readIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val readAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_agenda, "Mark as read", readPi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val last = lines.last()
        val builder = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(name)
            .setContentText(last.text)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setWhen(last.at)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(quiet)
            .setContentIntent(openIntent(ctx, agentId))
            .addAction(replyAction)
            .addAction(readAction)
        if (status != null) builder.setSubText(status)

        NotificationManagerCompat.from(ctx).notify(notificationId(agentId), builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun postSummary(ctx: Context) {
        val snapshot = synchronized(lock) { history.entries.map { it.key to it.value.toList() } }
        if (snapshot.isEmpty()) return
        val total = snapshot.sumOf { (_, lines) -> lines.count { !it.mine } }
        val inbox = NotificationCompat.InboxStyle()
        for ((id, lines) in snapshot) inbox.addLine("${displayName(id)}: ${lines.last().text}")
        inbox.setSummaryText("Convo")

        val summary = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("Convo")
            .setContentText("$total new message${if (total == 1) "" else "s"} from ${snapshot.size} agent${if (snapshot.size == 1) "" else "s"}")
            .setStyle(inbox)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, null))
            .build()
        NotificationManagerCompat.from(ctx).notify(SUMMARY_ID, summary)
    }
}
