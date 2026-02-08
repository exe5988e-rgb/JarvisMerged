//===== FILE: app/src/main/kotlin/com/jarvismini/service/ProgressReminderService.kt =====
package com.jarvismini.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.jarvismini.core.progress.ProgressStore

class ProgressReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val reminderRunnable = object : Runnable {
        override fun run() {
            checkForReminders()
            handler.postDelayed(this, 60_000) // check every minute
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(reminderRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(reminderRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForReminders() {
        val blocks = ProgressStore.getTodayBlocks()
        val now = System.currentTimeMillis()

        blocks.forEach { block ->
            // If block is not completed and scheduled time passed
            if (!block.completed && block.missedAt == null && now > block.scheduledAt) {
                ProgressStore.markMissed(block.id, now)
            }
        }
    }
}
