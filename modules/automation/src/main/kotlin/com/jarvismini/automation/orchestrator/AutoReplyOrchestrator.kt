package com.jarvismini.automation.orchestrator

import com.jarvismini.automation.AutoReplyDecisionEngine
import com.jarvismini.automation.decision.AutoReplyDecision
import com.jarvismini.automation.decision.NoReplyDecision
import com.jarvismini.automation.input.AutoReplyInput

object AutoReplyOrchestrator {

    fun handle(input: AutoReplyInput) {
        when (val decision = AutoReplyDecisionEngine.decide(input)) {
            is AutoReplyDecision -> sendResponse(decision.message)
            NoReplyDecision -> Unit
        }
    }

    private fun sendResponse(message: String) {
        println("Jarvis Auto-Reply: $message")
    }
}
