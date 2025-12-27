package com.jarvismini.callhandler.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState

class CallAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val text = event.text?.joinToString(" ") ?: return

        if (
            !text.contains("incoming", ignoreCase = true) &&
            !text.contains("calling", ignoreCase = true)
        ) return

        Log.d("CALL-A11Y", "Call UI detected: $text")

        if (JarvisState.currentMode == JarvisMode.NORMAL) return

        val number = extractPhoneNumber(text) ?: return

        val decision = AutoReplyOrchestrator.handle(
            AutoReplyInput(
                messageText = "Incoming call",
                isFromOwner = false
            )
        )

        if (decision !is ReplyDecision.AutoReply) return

        // ✅ NO direct service reference (IMPORTANT)
        val intent = Intent("com.jarvismini.ACTION_CALL_AUTO_REPLY").apply {
            putExtra("extra_number", number)
            putExtra("extra_message", decision.message)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
    }

    override fun onInterrupt() {}

    private fun extractPhoneNumber(text: String): String? {
        val regex = Regex("(\\+?\\d{10,13})")
        return regex.find(text)?.value
    }
}
