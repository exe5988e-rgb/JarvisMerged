package com.jarvismini.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvismini.smart.CooldownManager
import com.jarvismini.smart.SmartFilters
import com.jarvismini.smart.SleepMode
import com.jarvismini.engine.LLMManager

object AutomationEngine {

    private const val TAG = "JarvisMini-Automation"

    override fun toString(): String = TAG

    fun handleEvent(service: AccessibilityService, event: AccessibilityEvent?) {
        if (event == null) return
        if (SleepMode.enabled) return

        val root = service.rootInActiveWindow

        // ✅ STEP 0: Handle notifications first
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (root != null) ChatOpener.openChat(service, root)
            return   // Avoid double-trigger
        }

        val realRoot = root ?: return

        // STEP 1: Extract sender
        val sender = MessageExtractor.getSenderName(realRoot)
        if (sender.isBlank()) return

        // STEP 2: Extract last message
        val lastMsg = MessageExtractor.getLastIncomingMessage(realRoot)
        if (lastMsg.isBlank()) return

        // STEP 3: Smart filters
        if (!SmartFilters.isChatAllowed(realRoot, sender, lastMsg)) {
            Log.i(TAG, "SmartFilters blocked reply.")
            return
        }

        // STEP 4: Anti-spam
        if (!CooldownManager.canReply(sender)) {
            Log.i(TAG, "Cooldown block for: $sender")
            return
        }

        // STEP 5: AI Generate reply
        val reply = try {
            LLMManager.generateReply(lastMsg)
        } catch (e: Exception) {
            Log.e(TAG, "LLM failed: ${e.message}")
            return
        }

        if (reply.isBlank()) return

        // STEP 6: Insert into WhatsApp
        val input = NodeFinder.findInputField(realRoot)
        val sendBtn = NodeFinder.findSendButton(realRoot)

        if (input == null || sendBtn == null) {
            Log.e(TAG, "Input or Send button missing")
            return
        }

        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val bundle = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                reply
            )
        }
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        CooldownManager.markReplied(sender)
        Log.i(TAG, "Reply sent: $reply")

        // ✅ STEP 7: Auto close chat (optional)
        AutoCloser.closeChat(realRoot)
    }
}