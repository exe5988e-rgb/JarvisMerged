package com.jarvismini.callhandler

import android.content.Context
import android.util.Log

object SmsAutoReplyService {

    private const val AUTO_REPLY =
        "Hello Aamir sir, Jarvis here. I’ll get back to you shortly."

    fun reply(context: Context, number: String, incomingText: String) {
        SmsSender.send(context, number, AUTO_REPLY)
        Log.d("CALL-HANDLER", "Auto-reply sent to $number")
    }
}
