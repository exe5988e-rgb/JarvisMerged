package com.jarvismini.callhandler

import android.telephony.SmsManager

object SmsSender {

    fun send(context: android.content.Context, number: String, text: String) {
        val manager = SmsManager.getDefault()
        manager.sendTextMessage(number, null, text, null, null)
    }
}
