package com.jarvismini

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.jarvismini.core.progress.MissedTaskChecker
import com.jarvismini.core.progress.ProgressEngine

class ProgressReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val reminderInterval = 30 * 60 * 1000L // 30 minutes

    private val reminderRunnable = object : Runnable {
        override fun run() {
            // Check for incomplete tasks and remind via TTS
            val checker = MissedTaskChecker(this@ProgressReminderService)
            checker.checkAndRemind()
            
            // Schedule next check
            handler.postDelayed(this, reminderInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Start periodic reminders using ProgressEngine
        ProgressEngine.startPeriodicReminders(this)
        
        // Also use handler-based approach for backup
        handler.post(reminderRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(reminderRunnable)
        ProgressEngine.stopPeriodicReminders()
    }
}
