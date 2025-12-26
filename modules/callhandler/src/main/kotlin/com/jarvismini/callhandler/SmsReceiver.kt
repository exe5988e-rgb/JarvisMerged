// ===== FILE: modules/callhandler/src/main/kotlin/com/jarvismini/callhandler/SmsReceiver.kt =====
package com.jarvismini.callhandler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {

            val sender = msg.displayOriginatingAddress ?: continue
            val text = msg.messageBody ?: continue

            if (!ContactResolver.isContact(context, sender)) {
                Log.d("CALL-HANDLER", "SMS ignored (not contact): $sender")
                continue
            }

            SmsAutoReplyService.reply(context, sender, text)
        }
    }
}
