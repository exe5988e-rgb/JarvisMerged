package com.jarvismini.core.stopwatch

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Foreground service to show persistent notification for stopwatch
 */
class StopwatchService : Service() {

    companion object {
        private const val CHANNEL_ID = "stopwatch_channel"
        private const val NOTIF_ID = 1001

        /**
         * Start the stopwatch service safely depending on API level
         */
        fun start(context: Context) {
            val intent = Intent(context, StopwatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the stopwatch service
         */
        fun stop(context: Context) {
            val intent = Intent(context, StopwatchService::class.java)
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // Start foreground notification immediately
        startForeground(NOTIF_ID, buildNotification("00:00"))

        // Update notification periodically while stopwatch is running
        scope.launch {
            while (StopwatchManager.state.value.isRunning) {
                val elapsed = StopwatchManager.getCurrentElapsed()
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(
                    NOTIF_ID,
                    buildNotification(StopwatchManager.formatElapsedTimeShort(elapsed))
                )
                delay(500)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Build the persistent stopwatch notification
     */
    private fun buildNotification(time: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stopwatch Running")
            .setContentText("Elapsed: $time")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    /**
     * Create notification channel if it doesn't exist
     */
    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stopwatch",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }
}
