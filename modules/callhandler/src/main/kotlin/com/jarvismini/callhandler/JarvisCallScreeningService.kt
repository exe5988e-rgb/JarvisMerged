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
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.LockdownManager
import com.jarvismini.core.announce.AnnouncePriority
import com.jarvismini.core.announce.AnnouncementEngine
import com.jarvismini.core.announce.AskHook
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
                val who = displayName(number) ?: number ?: "a hidden number"
                val replied = number != null && sendAutoReply(number, LOCKDOWN_REPLY)
                EventLog.add(
                    EventType.CALL_BLOCKED,
                    if (replied) "Call blocked in lockdown, auto-reply sent" else "Call blocked in lockdown",
                    who
                )
                val report = "Sir, I blocked a call from $who" +
                    (if (replied) ", and sent them an automatic reply saying you are unavailable." else ".")
                val key = "call_blocked_" + (number ?: "hidden")
                val ask = AskHook.ask
                if (ask != null && number != null) {
                    ask("$report Shall I let $who through next time?", key) {
                        if (LockdownManager.addToWhitelist(number)) "Done, Sir. $who is on the whitelist and will ring through."
                        else "I could not add $who, Sir. The whitelist may be full."
                    }
                } else {
                    AnnouncementEngine.announce(report, AnnouncePriority.HIGH, key)
                }
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
            val who = displayName(number) ?: number
            val replied = sendAutoReply(number, decision.message)
            EventLog.add(
                EventType.CALL_BLOCKED,
                if (replied) "Call declined, auto-reply sent" else "Call declined",
                who
            )
            AnnouncementEngine.announce(
                "Sir, $who called while you were busy. I declined the call" +
                    (if (replied) " and replied via auto reply: ${decision.message}" else "."),
                AnnouncePriority.HIGH,
                "call_declined_$number"
            )
            reject(details)
        } else {
            allow(details)
        }
    }

    /** Sends the SMS unless this number already got an auto-reply in the last 10 minutes. */
    private fun sendAutoReply(number: String, message: String): Boolean {
        val key = "callreply_" + number.filter { it.isDigit() }
        val now = System.currentTimeMillis()
        if (now - JarvisPrefs.getLong(key, 0L) < 10 * 60_000L) return true  // already replied recently
        return try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            JarvisPrefs.putLong(key, now)
            true
        } catch (e: Exception) {
            Log.w("JarvisCall", "Auto-reply SMS failed: ${e.message}")
            false
        }
    }

    private fun displayName(number: String?): String? {
        if (number == null) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null).use { c ->
                if (c != null && c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private companion object {
        const val LOCKDOWN_REPLY =
            "Hi, I can't take calls right now. I'll get back to you when I'm free. (Automated reply sent by my assistant)"
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
