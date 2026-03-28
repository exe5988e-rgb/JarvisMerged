package com.jarvismini.core.workmode

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class WorkModeService : Service() {

    private val channelId = "jarvis_workmode"
    private var startTime = 0L
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startTime = System.currentTimeMillis()
        startForeground(1, buildNotification("00:00:00"))

        scope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val text = formatTime(elapsed)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(1, buildNotification(text))
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(time: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("JARVIS WORK MODE")
            .setContentText("Focus Time: $time")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "Work Mode",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val hrs = totalSec / 3600
        val min = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return "%02d:%02d:%02d".format(hrs, min, sec)
    }
}
