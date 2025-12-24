// ===== FILE: app/src/main/java/com/jarvismini/automation/WhatsAppNotificationListener.kt =====
package com.jarvismini.automation

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

class WhatsAppNotificationListener : NotificationListenerService() {

    private val TAG = "JARVIS-NOTIF"
    private val WHATSAPP = "com.whatsapp"
    private val REPLY_TEXT = "Hello Mr Shams, Jarvis here."

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != WHATSAPP) return

        val notification = sbn.notification
        val actions = notification.actions ?: return

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue

            for (remoteInput in remoteInputs) {
                if (remoteInput.allowFreeFormInput) {

                    val replyIntent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, REPLY_TEXT)
                    RemoteInput.addResultsToIntent(
                        arrayOf(remoteInput),
                        replyIntent,
                        bundle
                    )

                    try {
                        action.actionIntent.send(this, 0, replyIntent)
                        Toast.makeText(
                            this,
                            "Jarvis auto-replied",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.e(TAG, "AUTO-REPLY SENT")
                        return
                    } catch (e: PendingIntent.CanceledException) {
                        Log.e(TAG, "FAILED TO SEND REPLY", e)
                    }
                }
            }
        }
    }
}
