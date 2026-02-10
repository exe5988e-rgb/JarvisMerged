package com.jarvismini.engine

import android.content.Context

/**
 * ✅ FIXED: Updated to match new suspend interface
 */
object LocalRuleLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // No-op (local rule engine)
    }

    override suspend fun generateReply(prompt: String): String {
        return "Processing: $prompt"
    }
}
