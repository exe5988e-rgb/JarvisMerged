package com.jarvismini.core.routine

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Task-specific countdown timer service.
 *
 * FIX: Now ticks every second (was every 60s) and calls
 * TaskTimerManager.updateTimer() on each tick so the floating overlay
 * StateFlow stays live and the display is dynamic.
 *
 * Also accepts EXTRA_TASK_ID so updateTimer() can target the right entry.
 */
class TaskTimerService : Service() {

    companion object {
        private const val CHANNEL_ID    = "task_timer_channel"
        private const val TAG           = "TaskTimerService"
        private var notifIdCounter      = 2000

        fun start(context: Context, taskId: String, taskName: String, durationMinutes: Long) {
            val intent = Intent(context, TaskTimerService::class.java).apply {
                putExtra("task_id",          taskId)
                putExtra("task_name",        taskName)
                putExtra("duration_minutes", durationMinutes)
                putExtra("notif_id",         notifIdCounter++)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(intent)
                else
                    context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }
    }

    private val scope    = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null
    private var notifId: Int   = 2000

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId          = intent?.getStringExtra("task_id")              ?: "task"
        val taskName        = intent?.getStringExtra("task_name")            ?: "Task"
        val durationMinutes = intent?.getLongExtra("duration_minutes", 60)   ?: 60
        notifId             = intent?.getIntExtra("notif_id", 2000)          ?: 2000

        android.util.Log.d(TAG, "Starting timer for $taskName: $durationMinutes minutes")

        try {
            val totalSeconds = durationMinutes * 60
            val notification = buildNotification(taskName, totalSeconds, totalSeconds)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                startForeground(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else
                startForeground(notifId, notification)

            startCountdown(taskId, taskName, totalSeconds)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground: ${e.message}", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    /**
     * Ticks every second. Pushes remainingSeconds into TaskTimerManager so
     * the floating overlay Composable (collecting activeTimers StateFlow) updates live.
     * Notification updates every 10 seconds to avoid binder spam.
     */
    private fun startCountdown(taskId: String, taskName: String, totalSeconds: Long) {
        timerJob?.cancel()

        timerJob = scope.launch {
            var remaining = totalSeconds

            while (remaining > 0 && isActive) {
                // Push to StateFlow every second → floating overlay stays live
                TaskTimerManager.updateTimer(taskId, remaining)

                // Update notification every 10 seconds (saves binder calls)
                if (remaining % 10 == 0L) {
                    updateNotification(taskName, remaining, totalSeconds)
                }

                delay(1_000)
                remaining--
            }

            // Final update at 0
            TaskTimerManager.updateTimer(taskId, 0)

            if (remaining <= 0) showCompletionNotification(taskName)
            stopSelf()
        }
    }

    private fun updateNotification(taskName: String, remaining: Long, total: Long) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(notifId, buildNotification(taskName, remaining, total))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update notification: ${e.message}")
        }
    }

    private fun showCompletionNotification(taskName: String) {
        try {
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⏰ Time's Up!")
                .setContentText("$taskName duration completed")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java)?.notify(notifId + 10000, n)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show completion notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        android.util.Log.d(TAG, "Service onDestroy called")
        timerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Notification shows seconds-accurate remaining time */
    private fun buildNotification(taskName: String, remaining: Long, total: Long): Notification {
        val h = remaining / 3600
        val m = (remaining % 3600) / 60
        val s = remaining % 60

        val timeText = when {
            h > 0 -> String.format("%dh %02dm %02ds remaining", h, m, s)
            else  -> String.format("%d min %02d sec remaining", m, s)
        }

        val progress = ((total - remaining) * 100 / total).toInt()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏱️ $taskName")
            .setContentText(timeText)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Task Timers", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows countdown timers for tasks"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
