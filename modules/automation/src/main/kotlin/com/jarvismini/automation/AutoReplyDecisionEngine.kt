package com.jarvismini.automation

import com.jarvismini.core.JarvisMode
import com.jarvismini.engine.ReplyReason

object AutoReplyDecisionEngine {

    fun decide(context: AutoReplyContext): ReplyDecision {

        if (context.isOwner) return ReplyDecision.NoReply
        if (context.isGroupChat) return ReplyDecision.NoReply
        if (isLowSignalMessage(context.messageText)) return ReplyDecision.NoReply
        if (isInCooldown(context.senderId, context.timestamp)) return ReplyDecision.NoReply
        if (context.currentMode == JarvisMode.SLEEP) return ReplyDecision.NoReply

        return ReplyDecision.Reply(ReplyReason.USER_BUSY)
    }

    private fun isLowSignalMessage(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized.isEmpty() ||
                normalized in setOf("ok", "okay", "thanks", "thank you", "👍", "👌")
    }

    private fun isInCooldown(senderId: String, timestamp: Long): Boolean {
        // Stub — persistence later
        return false
    }
}
