package com.jarvismini.callhandler.logic

import android.content.Context
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import java.util.concurrent.ConcurrentHashMap

object CallHandlerEngine {

    private const val TAG = "CALL-HANDLER"
    private const val COOLDOWN_MS = 60_000L

    private val lastHandled = ConcurrentHashMap<String, Long>()

    fun handleIncomingCall(context: Context, number: String) {
        val now = System.currentTimeMillis()

        // 🔕 Jarvis OFF → do nothing
        if (JarvisState.currentMode == JarvisMode.NORMAL) {
            Log.d(TAG, "NORMAL mode → allow call")
            return
        }

        // 🔒 Contacts only
        if (!isSavedContact(context, number)) {
            Log.d(TAG, "Ignored (not contact)")
            return
        }

        // 🔁 Cooldown
        val last = lastHandled[number] ?: 0L
        if (now - last < COOLDOWN_MS) {
            Log.d(TAG, "Cooldown active")
            return
        }
        lastHandled[number] = now

        // 🧠 Orchestrator decision
        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput(
                messageText = "Incoming call",
                isFromOwner = false
            )
        )

        if (decision is ReplyDecision.AutoReply) {
            sendSms(number, decision.message)
            Log.d(TAG, "Auto-reply sent")
        }
    }

    private fun isSavedContact(context: Context, number: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.NUMBER),
            "${ContactsContract.PhoneLookup.NUMBER} = ?",
            arrayOf(number),
            null
        )
        return cursor?.use { it.moveToFirst() } == true
    }

    private fun sendSms(number: String, message: String) {
        SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
    }
}
