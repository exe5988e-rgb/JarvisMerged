package com.jarvismini

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator

class AppAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val source: AccessibilityNodeInfo? = event.source
            val messageText = source?.text?.toString()?.trim()
            if (messageText.isNullOrEmpty()) return

            val input = AutoReplyInput(
                messageText = messageText,
                isFromOwner = false
            )

            val decision = AutoReplyOrchestrator.handle(input)

            when (decision) {
                is ReplyDecision.AutoReply ->
                    println("Jarvis Auto-Reply: ${decision.message}")

                ReplyDecision.NoReply ->
                    println("Jarvis will not reply")
            }
        }
    }

    override fun onInterrupt() {
        // required
    }
}
