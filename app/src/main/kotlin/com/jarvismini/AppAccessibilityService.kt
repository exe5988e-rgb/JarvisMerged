package com.jarvismini

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.decision.AutoReplyDecision
import com.jarvismini.automation.decision.NoReplyDecision

/**
 * Accessibility service to monitor incoming messages
 * and trigger Jarvis automation.
 */
class AppAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only process new content (message arrival)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val source: AccessibilityNodeInfo? = event.source
            val messageText = source?.text?.toString()?.trim()
            if (messageText.isNullOrEmpty()) return

            // Build input object for automation
            val input = AutoReplyInput(
                messageText = messageText,
                isFromOwner = false // Placeholder: later detect sender
            )

            // Forward to orchestrator
            val decision = AutoReplyOrchestrator.handle(input)

            // Log the decision for testing
            when (decision) {
                is AutoReplyDecision -> {
                    println("Jarvis Auto-Reply: ${decision.message}")
                }
                is NoReplyDecision -> {
                    println("Jarvis will not reply")
                }
            }
        }
    }

    override fun onInterrupt() {
        // Required override; can log if needed
        println("AccessibilityService interrupted")
    }
}
