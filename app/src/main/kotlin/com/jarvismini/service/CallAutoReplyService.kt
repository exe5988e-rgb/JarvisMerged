package com.jarvismini.callhandler.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import android.util.Log
import com.jarvismini.R

class CallAutoReplyService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "Foreground Service STARTED")

        startForeground(NOTIFICATION_ID, createNotification())

        val number = intent?.getStringExtra(EXTRA_NUMBER)
        val message = intent?.getStringExtra(EXTRA_MESSAGE)

        if (!number.isNullOrBlank() && !message.isNullOrBlank()) {
            SmsManager.getDefault()
                .sendTextMessage(number, null, message, null, null)
        } else {
            Log.e(TAG, "Missing number or message")
        }

        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Call Auto‑Reply",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText("Auto‑replying to incoming call")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_MESSAGE = "extra_message"
        const val CHANNEL_ID = "jarvis_call_reply"
        const val NOTIFICATION_ID = 1002
        private const val TAG = "CALL-FGS"
    }
}
