package com.jarvismini.engine

class LocalProvider : LLMProvider {
    override suspend fun generateReply(prompt: String): String {
        return "I'm here! (Offline mode). How can I help?"
    }
}