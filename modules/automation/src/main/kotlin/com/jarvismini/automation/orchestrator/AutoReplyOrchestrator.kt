package com.jarvismini.automation.orchestrator

import com.jarvismini.automation.decision.ReplyDecision
import com.jarvismini.automation.decision.ReplyDecision.AutoReply
import com.jarvismini.automation.decision.ReplyDecision.NoReply
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState

object AutoReplyOrchestrator {

    fun decide(inputText: String): ReplyDecision {
        return when (JarvisState.currentMode) {

            JarvisMode.SLEEP ->
                NoReply

            JarvisMode.FOCUS ->
                NoReply

            JarvisMode.DRIVING ->
                AutoReply(
                    message = "I'm driving right now. Will get back soon.",
                    reason = "DRIVING_MODE"
                )

            JarvisMode.WORK ->
                AutoReply(
                    message = "I'm working at the moment. I'll respond later.",
                    reason = "WORK_MODE"
                )

            JarvisMode.NORMAL ->
                NoReply
        }
    }
}
