package com.jarvismini.core

object AssistantVoice {

    fun driving(): String =
        "${AssistantProfile.ASSISTANT_NAME}: ${AssistantProfile.addressUser()} is currently driving and will respond shortly."

    fun working(): String =
        "${AssistantProfile.ASSISTANT_NAME}: ${AssistantProfile.addressUser()} is working at the moment. Jarvis will notify him."

    fun unavailable(reason: String): String =
        "${AssistantProfile.ASSISTANT_NAME}: ${AssistantProfile.addressUser()} is unavailable right now ($reason)."

    fun noReply(): String =
        "${AssistantProfile.ASSISTANT_NAME}: No reply will be sent at this time."
}
