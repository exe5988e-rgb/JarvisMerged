package com.jarvismini.core

object AssistantProfile {

    const val ASSISTANT_NAME = "Jarvis"

    // Default addressing (as you requested)
    const val USER_PRIMARY = "Aamir Sir"
    const val USER_FORMAL = "Mr Shams"

    // Used everywhere
    fun addressUser(): String = USER_PRIMARY
}
