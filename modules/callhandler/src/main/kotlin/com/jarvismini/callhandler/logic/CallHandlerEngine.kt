package com.jarvismini.callhandler.logic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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

        if (JarvisState.currentMode == JarvisMode.NORMAL) return

        if (!isSavedContact(context, number)) return

        val last = lastHandled[number] ?: 0L
        if (now - last < COOLDOWN_MS) return
        lastHandled[number] = now

        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput("Incoming call", false)
        )

        if (decision !is ReplyDecision.AutoReply) return

        // 🔥 DO NOT reference app service class
        val intent = Intent("com.jarvismini.ACTION_CALL_AUTO_REPLY").apply {
            setPackage(context.packageName)
            putExtra("extra_number", number)
            putExtra("extra_message", decision.message)
        }

        context.startForegroundService(intent)
    }

    private fun isSavedContact(context: Context, number: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(Uri.encode(number))
            .build()

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )

        return cursor?.use { it.moveToFirst() } == true
    }
}
