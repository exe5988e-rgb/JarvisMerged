package com.jarvismini.engine

import com.jarvismini.core.JarvisMode

object JarvisReplyGenerator {

    fun generate(
        incomingMessage: String,
        reason: ReplyReason,
        mode: JarvisMode
    ): String {

        val baseReply = ReplyToneResolver.resolve(reason, mode)

        return "$baseReply\n\nMessage received: \"$incomingMessage\""
    }
}
