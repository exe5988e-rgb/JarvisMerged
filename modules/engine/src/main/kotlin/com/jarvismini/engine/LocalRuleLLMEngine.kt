package com.jarvismini.engine

import android.content.Context

object LocalRuleLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // No-op (local rule engine)
    }

    override fun generateReply(prompt: String): String {
        return "Processing: $prompt"
    }
}
