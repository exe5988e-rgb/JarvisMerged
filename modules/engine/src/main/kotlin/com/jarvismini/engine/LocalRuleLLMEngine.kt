package com.jarvismini.engine

import android.content.Context

/**
 * Simple rule-based LLM engine for testing and fallback.
 * 
 * This implementation doesn't use actual AI models but provides
 * rule-based responses. Useful for:
 * - Testing the UI without loading large models
 * - Fallback when models fail to load
 * - Quick prototyping
 */
object LocalRuleLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // No initialization needed for rule-based engine
    }

    /**
     * Generate a simple rule-based response.
     * 
     * This is a suspend function to match the interface,
     * but it returns immediately since no heavy computation is done.
     */
    override suspend fun generateReply(prompt: String): String {
        return "Processing: $prompt"
    }
}
