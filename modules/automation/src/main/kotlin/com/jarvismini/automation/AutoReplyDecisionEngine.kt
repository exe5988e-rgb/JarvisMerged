package com.jarvismini.automation.decision

sealed interface ReplyDecision

data class AutoReplyDecision(
    val message: String
) : ReplyDecision

object NoReplyDecision : ReplyDecision
