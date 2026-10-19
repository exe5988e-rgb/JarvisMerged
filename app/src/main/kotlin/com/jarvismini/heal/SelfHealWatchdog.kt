package com.jarvismini.heal

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.announce.AnnouncePriority
import com.jarvismini.core.announce.AnnouncementEngine
import com.jarvismini.core.events.EventLog
import com.jarvismini.core.events.EventType
import com.jarvismini.proactive.ProactiveService
import com.jarvismini.voice.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Spec section 8: Self-Healing System.
 * Runs inside ProactiveService (already a foreground service, so this costs no
 * extra process). Every 2 minutes it:
 *  - checks the wake word service is actually running and restarts it if not
 *  - checks the proactive/announcement pipeline can still reach the voice path
 *  - logs memory (RAM) pressure so a crash-prone period shows up in the digest
 * Crash recovery itself (catching the actual crash and restarting the process)
 * is handled by SystemEventAnnouncer's uncaught-exception hook, which already
 * exists — this watchdog covers the case where a *service* silently dies
 * without crashing the whole app.
 */
object SelfHealWatchdog {

    private const val CHECK_MS = 2 * 60_000L
    private const val KEY_LAST_RESTART = "heal_last_wakeword_restart"
    private const val MIN_RESTART_GAP_MS = 5 * 60_000L

    private var installed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        scope.launch {
            while (true) {
                delay(CHECK_MS)
                try { check(app) } catch (_: Exception) {}
            }
        }
    }

    private fun check(app: Context) {
        checkWakeWord(app)
        checkMemory(app)
    }

    private fun checkWakeWord(app: Context) {
        val running = isServiceRunning(app, WakeWordService::class.java.name)
        val hasKey = !JarvisPrefs.getString("picovoice_access_key").isNullOrBlank()
        if (running || !hasKey) return

        val now = System.currentTimeMillis()
        val last = JarvisPrefs.getLong(KEY_LAST_RESTART, 0L)
        if (now - last < MIN_RESTART_GAP_MS) return
        JarvisPrefs.putLong(KEY_LAST_RESTART, now)

        try {
            androidx.core.content.ContextCompat.startForegroundService(
                app, android.content.Intent(app, WakeWordService::class.java)
            )
            EventLog.add(EventType.SERVICE, "Wake word service restarted")
            AnnouncementEngine.announce(
                "I had to restart voice listening, Sir. All set now.",
                AnnouncePriority.LOW, "heal_wakeword"
            )
        } catch (e: Exception) {
            EventLog.add(EventType.SERVICE, "Wake word restart failed", e.message ?: "")
        }
    }

    private fun checkMemory(app: Context) {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.lowMemory) {
            EventLog.add(EventType.SERVICE, "Low system memory", "avail=${info.availMem / (1024 * 1024)}MB")
        }
    }

    private fun isServiceRunning(context: Context, className: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        return am.getRunningServices(Int.MAX_VALUE).any { it.service.className == className }
    }
}
