package com.jarvismini.automation.orchestrator

import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.input.AutoReplyInput
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState

/**
 * Orchestrates automation decisions for incoming messages.
 */
object AutoReplyOrchestrator {

    fun handle(input: AutoReplyInput): ReplyDecision {
        return when (JarvisState.currentMode) {
            JarvisMode.SLEEP,
            JarvisMode.FOCUS -> ReplyDecision.NoReply

            JarvisMode.DRIVING ->
                ReplyDecision.AutoReply(
                    message = "I'm driving right now. I'll respond soon."
                )

            JarvisMode.WORK ->
                ReplyDecision.AutoReply(
                    message = "I'm working at the moment. Will reply later."
                )

            JarvisMode.NORMAL ->
                ReplyDecision.NoReply
        }
    }

    fun init() {
        // future setup hook
    }
}    }
}
