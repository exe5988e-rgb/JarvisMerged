package com.jarvismini.engine

interface LLMProvider {
    suspend fun generateReply(prompt: String): String
}
