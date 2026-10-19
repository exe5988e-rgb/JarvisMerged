package com.jarvismini.core

/**
 * Auto-reply wording. Written in the owner's own first-person voice (no "Jarvis" context),
 * with a light tag so the recipient knows it is automated.
 */
object AssistantVoice {

    const val AUTO_TAG = "_automated message_"

    private fun tagged(text: String) = "$text\n$AUTO_TAG"

    fun driving(): String =
        tagged("I'm driving right now, will get back to you once I'm done.")

    fun working(): String =
        tagged("I'm currently busy at the moment, will get back to you shortly.")

    fun sleeping(): String =
        tagged("I'm sleeping right now, will reply once I'm up.")

    fun focus(): String =
        tagged("I'm in the middle of something right now, will get back to you shortly.")

    fun unavailable(reason: String): String =
        tagged("I'm not available right now ($reason), will get back to you soon.")

    /** Used for AI-written replies so they carry the same tag. */
    fun withTag(text: String): String = tagged(text.trim())

    fun noReply(): String =
        "No reply will be sent at this time."
}
