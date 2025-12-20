package com.jarvismini.automation.orchestrator

import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.core.AssistantVoice
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState

/**
 * Orchestrates automation decisions for incoming messages.
 * Pure decision layer — no hardcoded user-facing text.
 */
object AutoReplyOrchestrator {

    fun handle(input: AutoReplyInput): ReplyDecision {
        return when (JarvisState.currentMode) {

            JarvisMode.DRIVING ->
                ReplyDecision.AutoReply(
                    message = AssistantVoice.driving()
                )

            JarvisMode.WORK ->
                ReplyDecision.AutoReply(
                    message = AssistantVoice.working()
                )

            JarvisMode.SLEEP ->
                ReplyDecision.AutoReply(
                    message = AssistantVoice.unavailable("sleeping")
                )

            JarvisMode.FOCUS ->
                ReplyDecision.AutoReply(
                    message = AssistantVoice.unavailable("focus mode")
                )

            JarvisMode.NORMAL ->
                ReplyDecision.NoReply
        }
    }

    fun init() {
        // reserved for future signal wiring
    }
}
