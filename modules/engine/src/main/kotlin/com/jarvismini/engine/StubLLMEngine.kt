package com.jarvismini.engine

import android.content.Context

/**
 * Stub LLM engine for testing without actual model loading.
 * 
 * Returns simple echo responses. Useful for:
 * - Testing the UI flow without models
 * - Debugging coroutine handling
 * - Quick development iterations
 */
object StubLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // No initialization needed
    }

    /**
     * Generate a stub response that echoes the prompt.
     * 
     * This is a suspend function to match the interface.
     */
    override suspend fun generateReply(prompt: String): String {
        // Simple echo response
        return "Stub response for: $prompt"
    }
}
