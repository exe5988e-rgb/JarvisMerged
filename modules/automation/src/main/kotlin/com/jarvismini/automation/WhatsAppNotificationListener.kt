package com.jarvismini.automation

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast

class WhatsAppNotificationListener : NotificationListenerService() {

    private val TAG = "JARVIS-NOTIF"
    private val WHATSAPP = "com.whatsapp"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != WHATSAPP) return

        val notification = sbn.notification
        val intent = notification.contentIntent ?: return

        try {
            intent.send()
            Toast.makeText(
                this,
                "Jarvis opened WhatsApp chat",
                Toast.LENGTH_SHORT
            ).show()
            Log.e(TAG, "WHATSAPP CHAT OPENED FROM NOTIFICATION")
        } catch (e: Exception) {
            Log.e(TAG, "FAILED TO OPEN CHAT", e)
        }
    }
}
