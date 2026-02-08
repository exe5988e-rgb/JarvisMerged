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
 * NEW FEATURE: Task-specific countdown timer service
 * 
 * Shows a persistent notification with countdown timer for each task
 * Triggered by routine actions with type "start_timer"
 * 
 * Example JSON action:
 * {
 *   "type": "start_timer",
 *   "task": "Morning Lectures",
 *   "duration": "360"  // minutes
 * }
 */
class TaskTimerService : Service() {

    companion object {
        private const val CHANNEL_ID = "task_timer_channel"
        private const val TAG = "TaskTimerService"
        
        private var notifIdCounter = 2000
        
        fun start(context: Context, taskName: String, durationMinutes: Long) {
            val intent = Intent(context, TaskTimerService::class.java)
            intent.putExtra("task_name", taskName)
            intent.putExtra("duration_minutes", durationMinutes)
            intent.putExtra("notif_id", notifIdCounter++)
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null
    private var notifId: Int = 2000

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskName = intent?.getStringExtra("task_name") ?: "Task"
        val durationMinutes = intent?.getLongExtra("duration_minutes", 60) ?: 60
        notifId = intent?.getIntExtra("notif_id", 2000) ?: 2000
        
        android.util.Log.d(TAG, "Starting timer for $taskName: $durationMinutes minutes")
        
        try {
            // Start foreground immediately
            val notification = buildNotification(taskName, durationMinutes, durationMinutes)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    notifId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(notifId, notification)
            }
            
            // Start countdown
            startCountdown(taskName, durationMinutes)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground: ${e.message}", e)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    private fun startCountdown(taskName: String, totalMinutes: Long) {
        timerJob?.cancel()
        
        timerJob = scope.launch {
            var remainingMinutes = totalMinutes
            
            while (remainingMinutes > 0 && isActive) {
                updateNotification(taskName, remainingMinutes, totalMinutes)
                delay(60_000) // Update every minute
                remainingMinutes--
            }
            
            // Timer completed
            if (remainingMinutes <= 0) {
                showCompletionNotification(taskName)
            }
            
            stopSelf()
        }
    }

    private fun updateNotification(taskName: String, remaining: Long, total: Long) {
        try {
            val notification = buildNotification(taskName, remaining, total)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(notifId, notification)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update notification: ${e.message}")
        }
    }

    private fun showCompletionNotification(taskName: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⏰ Time's Up!")
                .setContentText("$taskName duration completed")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(notifId + 10000, notification)
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

    private fun buildNotification(taskName: String, remaining: Long, total: Long): Notification {
        val hours = remaining / 60
        val minutes = remaining % 60
        
        val timeText = if (hours > 0) {
            String.format("%dh %02dm remaining", hours, minutes)
        } else {
            String.format("%d min remaining", minutes)
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
                CHANNEL_ID,
                "Task Timers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows countdown timers for tasks"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
