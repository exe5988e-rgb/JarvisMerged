package com.jarvismini.automation

import com.jarvismini.engine.EngineProvider
import com.jarvismini.engine.AutoReplyInput

object AutoReplyOrchestrator {

    fun handleIncomingMessage(context: AutoReplyContext): String? {

        // STEP 2 — Decide (who / what)
        val decision = AutoReplyDecisionEngine.decide(context)
        if (decision is ReplyDecision.NoReply) {
            return null
        }

        // STEP 5 — Mode guard (when)
        if (!ModeGuard.allowsReply()) {
            return null
        }

        // STEP 3 — Generate reply (how)
        val replyInput = AutoReplyInput(
            senderName = context.senderName,
            messageText = context.messageText,
            reason = decision.reason
        )

        val rawReply = EngineProvider.engine.generateReply(replyInput)
        return sanitize(rawReply)
    }

    private fun sanitize(reply: String): String {
        return reply
            .take(200)
            .replace(Regex("\\?$"), "")
    }
}
