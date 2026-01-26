package com.jarvismini

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.tts.AssistantTTS
import kotlinx.coroutines.*

class ProgressReminderService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val CHANNEL_ID = "progress_reminder_channel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring missed tasks…"))

        serviceScope.launch {
            while (isActive) {
                checkMissedTasks()
                delay(15 * 60 * 1000L)
            }
        }
    }

    private suspend fun checkMissedTasks() {
        ProgressRepository.hydrate(this)
        val todayBlocks = ProgressRepository.getTodayBlocks(this)
        val missedBlocks = todayBlocks.filter { !it.completed && it.id.isNotEmpty() }

        missedBlocks.forEach { block ->
            AssistantTTS.speak(this, "Reminder: you missed task ${block.id}. Please complete it.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Progress Reminders",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Mini")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); serviceScope.cancel() }
}
