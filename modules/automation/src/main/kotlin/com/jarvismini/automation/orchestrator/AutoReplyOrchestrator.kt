package com.jarvismini.automation.orchestrator

import com.jarvismini.automation.AutoReplyDecisionEngine
import com.jarvismini.automation.decision.AutoReplyDecision
import com.jarvismini.automation.decision.NoReplyDecision
import com.jarvismini.automation.input.AutoReplyInput

object AutoReplyOrchestrator {

    fun handle(input: AutoReplyInput) {
        val decision = AutoReplyDecisionEngine.decide(input)

        when (decision) {
            is AutoReplyDecision -> sendResponse(decision.message)
            NoReplyDecision -> Unit
        }
    }

    private fun sendResponse(message: String) {
        // Stub: integrate WhatsApp / notification reply later
        println("Jarvis Auto-Reply: $message")
    }
}
