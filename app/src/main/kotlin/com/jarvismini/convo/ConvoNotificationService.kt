package com.jarvismini.convo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls the Convo server while the app is in the background and raises
 * WhatsApp-style notifications for new messages addressed to the admin.
 *
 * It uses the same repository and encrypted settings as the screen. It stops itself when
 * notifications are switched off or the admin token is removed. Poll errors back off quietly
 * and never produce a notification.
 */
class ConvoNotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ConvoNotifier.ensureChannels(this)
        // Type comes from the manifest (specialUse), same pattern as ProactiveService.
        startForeground(ConvoNotifier.SERVICE_ID, ConvoNotifier.serviceNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive != true) {
            job = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        val app = applicationContext
        val settings = EncryptedConvoSettings(app)
        val repo = HttpConvoRepository(baseUrl = { settings.baseUrl }, token = { settings.token })
        var wait = POLL_MS
        while (currentCoroutineContext().isActive) {
            if (!ConvoNotifier.isEnabled(app) || settings.token.isBlank()) {
                stopSelf()
                return
            }
            val ok = try {
                pollOnce(repo)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "poll failed: ${e.javaClass.simpleName}")
                false
            }
            wait = if (ok) POLL_MS else (wait * 2).coerceAtMost(MAX_BACKOFF_MS)
            delay(wait)
        }
    }

    /** Returns false when the server could not be reached, so the loop backs off. */
    private suspend fun pollOnce(repo: ConvoRepository): Boolean {
        val app = applicationContext
        var cursor = ConvoNotifier.lastNotifiedId(app)

        if (cursor < 0L) {
            // First run: start from "now" so old history never floods the shade.
            val page = repo.messages(beforeId = LATEST, limit = 1).getOrElse { return false }
            cursor = page.messages.maxOfOrNull { it.id } ?: 0L
            page.nextCursor?.let { if (it > cursor) cursor = it }
            ConvoNotifier.setLastNotifiedId(app, cursor)
            return true
        }

        val fresh = ArrayList<ConvoMessage>()
        var pages = 0
        while (pages++ < MAX_PAGES) {
            val page = repo.messages(afterId = cursor, limit = PAGE_LIMIT).getOrElse { return false }
            fresh.addAll(page.messages)
            page.messages.maxOfOrNull { it.id }?.let { if (it > cursor) cursor = it }
            page.nextCursor?.let { if (it > cursor) cursor = it }
            if (page.messages.size < PAGE_LIMIT) break
        }
        if (fresh.isEmpty()) return true

        ConvoNotifier.setLastNotifiedId(app, cursor)

        val incoming = fresh.filter { it.recipientId == ADMIN_ID && it.senderId != ADMIN_ID }
        if (incoming.isNotEmpty() && !ConvoNotifier.screenVisible) {
            val agents = repo.agents().getOrNull().orEmpty()
            ConvoNotifier.showIncoming(app, incoming, agents)
        }
        return true
    }

    companion object {
        private const val TAG = "ConvoNotificationService"
        private const val POLL_MS = 10_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val PAGE_LIMIT = 200
        private const val MAX_PAGES = 5
        private const val LATEST = 2_000_000_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, ConvoNotificationService::class.java),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, ConvoNotificationService::class.java),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
        }

        /** Called from MainActivity on launch: cheap prefs check only; the service validates the token itself. */
        fun startIfEnabled(context: Context) {
            if (ConvoNotifier.isEnabled(context)) start(context)
        }
    }
}
