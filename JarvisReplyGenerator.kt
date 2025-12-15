package com.jarvismini.engine

object JarvisReplyGenerator {

    fun generateReply(input: AutoReplyInput): String {
        // TEMPORARY STUB (until real LLM is wired)
        return when (input.reason) {
            ReplyReason.USER_BUSY ->
                "This is Jarvis, assisting Mr. Aamir. He is currently occupied and will respond when available."
            ReplyReason.MISSED_MESSAGE ->
                "Jarvis here, assisting Mr. Aamir. He will review this message shortly."
            ReplyReason.IMPORTANT_MESSAGE ->
                "This is Jarvis, assisting Mr. Aamir. He has been notified and will respond as soon as possible."
        }
    }
}
