package com.jarvismini.core.stopwatch

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.ContextCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Foreground service to show stopwatch notification
 */
class StopwatchService : Service() {

    companion object {
        private const val CHANNEL_ID = "stopwatch_channel"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, StopwatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StopwatchService::class.java)
            context.stopService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("00:00"))

        scope.launch {
            while (StopwatchManager.state.value.isRunning) {
                val elapsed = StopwatchManager.getCurrentElapsed()
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIF_ID, buildNotification(StopwatchManager.formatElapsedTimeShort(elapsed)))
                delay(500)
            }

            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(time: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stopwatch Running")
            .setContentText("Elapsed: $time")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
}
