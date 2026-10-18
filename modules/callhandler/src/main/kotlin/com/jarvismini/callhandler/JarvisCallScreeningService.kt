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
import com.jarvismini.core.LockdownManager
import com.jarvismini.core.events.EventLog
import com.jarvismini.core.events.EventType

class JarvisCallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart

        // Lockdown: only whitelisted numbers ring. Unknown / hidden numbers are blocked.
        if (LockdownManager.isEnabled()) {
            if (LockdownManager.isAllowed(number)) {
                allow(details)
            } else {
                EventLog.add(EventType.CALL_BLOCKED, "Call blocked in lockdown", number ?: "Hidden number")
                blockQuietly(details)
            }
            return
        }

        if (number == null) return

        Log.d("JarvisCall", "Incoming call from $number")

        // Jarvis NORMAL → allow call
        if (JarvisState.currentMode == JarvisMode.NORMAL) {
            allow(details)
            return
        }

        // Contacts only
        if (!isContact(number)) {
            allow(details)
            return
        }

        // Use SAME AutoReplyInput contract as rest of repo
        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput(
                messageText = "Incoming call",
                isFromOwner = false
            )
        )

        if (decision is ReplyDecision.AutoReply) {
            SmsManager.getDefault()
                .sendTextMessage(number, null, decision.message, null, null)

            reject(details)
        } else {
            allow(details)
        }
    }

    private fun allow(details: Call.Details) {
        respondToCall(
            details,
            CallResponse.Builder().build()
        )
    }

    // Keeps the call log entry so blocked calls stay visible, only silences the notification.
    private fun blockQuietly(details: Call.Details) {
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .build()
        )
    }

    private fun reject(details: Call.Details) {
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(true)
                .build()
        )
    }

    private fun isContact(number: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )

        contentResolver.query(uri, null, null, null, null).use { cursor ->
            return cursor?.moveToFirst() == true
        }
    }
}
