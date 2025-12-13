package com.jarvismini.automation

import com.jarvismini.engine.LLMManager

object SmartAutoReply {

    suspend fun generate(message: String): String {
        return LLMManager.generateReply(message)
    }
}
