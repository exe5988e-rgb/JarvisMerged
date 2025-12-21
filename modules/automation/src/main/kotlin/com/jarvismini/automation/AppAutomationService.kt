package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator

class AppAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisMini-AutoService"
    }

    override fun onServiceConnected() {
    super.onServiceConnected()
    Log.i(TAG, "Automation Service connected")

    val info = AccessibilityServiceInfo().apply {
        eventTypes =
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        notificationTimeout = 100
    }

    setServiceInfo(info)
}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                Log.i(TAG, "Notification detected")
                // Future: open chat using PendingIntent if needed
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val root = rootInActiveWindow ?: return

                val messageText = MessageExtractor.getLastIncomingMessage(root)
                if (messageText.isBlank()) return

                val input = AutoReplyInput(
                    messageText = messageText,
                    isFromOwner = false
                )

                val decision = AutoReplyOrchestrator.handle(input)

                when (decision) {
                    is ReplyDecision.AutoReply -> {
                        Log.i(TAG, "Auto replying: ${decision.message}")
                        sendMessage(root, decision.message)
                    }
                    ReplyDecision.NoReply -> {
                        Log.i(TAG, "No reply decision")
                    }
                }

                AutoCloser.closeChat(root)
            }
        }
    }

    private fun sendMessage(root: AccessibilityNodeInfo, message: String) {
        val inputField = NodeFinder.findInputField(root)
        val sendButton = NodeFinder.findSendButton(root)

        inputField?.let {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    message
                )
            }
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() {
        Log.i(TAG, "Automation Service interrupted")
    }
}
