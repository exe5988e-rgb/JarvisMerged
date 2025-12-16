package com.jarvismini.automation.decision

/**
 * Single, canonical reply decision model.
 * DO NOT duplicate this anywhere else.
 */
sealed class ReplyDecision {

    object NoReply : ReplyDecision()

    data class AutoReply(
        val message: String,
        val reason: String = ""
    ) : ReplyDecision()
}
