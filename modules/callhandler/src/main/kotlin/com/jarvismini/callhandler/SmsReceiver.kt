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
            val sender = msg.displayOriginatingAddress ?: return
            val text = msg.messageBody ?: return

            if (!ContactResolver.isContact(context, sender)) {
                Log.d("CALL-HANDLER", "SMS ignored (not contact)")
                return
            }

            SmsAutoReplyService.reply(context, sender, text)
        }
    }
}
