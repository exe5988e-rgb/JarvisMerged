package com.jarvismini.callhandler

import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SmsManager
import android.util.Log
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import java.util.concurrent.ConcurrentHashMap

class JarvisCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CALL-HANDLER"
        private const val COOLDOWN_MS = 60_000L
    }

    private val lastHandled = ConcurrentHashMap<String, Long>()

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle ?: return
        val number = handle.schemeSpecificPart ?: return
        val now = System.currentTimeMillis()

        // 🧠 ALWAYS RESPOND — even if letting it ring
        fun allow() {
            respondToCall(CallResponse.Builder().build())
        }

        // 🧠 JARVIS OFF → do nothing
        if (JarvisState.currentMode == JarvisMode.OFF) {
            allow()
            return
        }

        // 🔒 CONTACT-ONLY
        if (!isSavedContact(number)) {
            allow()
            return
        }

        // 🔁 COOLDOWN
        val last = lastHandled[number] ?: 0L
        if (now - last < COOLDOWN_MS) {
            allow()
            return
        }
        lastHandled[number] = now

        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput(
                messageText = "Incoming call",
                isFromOwner = false
            )
        )

        if (decision !is ReplyDecision.AutoReply) {
            allow()
            return
        }

        sendSms(number, decision.message)

        respondToCall(
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
                .build()
        )

        Log.d(TAG, "Call rejected + SMS sent")
    }

    private fun isSavedContact(number: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )

        contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )?.use { return it.moveToFirst() }

        return false
    }

    private fun sendSms(number: String, message: String) {
        try {
            SmsManager.getDefault()
                .sendTextMessage(number, null, message, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed", e)
        }
    }
}
