package com.jarvismini.engine

import android.content.Context

class LocalRuleLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // No-op (local engine)
    }

    override fun generateReply(prompt: String): String {
        return "Processing: $prompt"
    }
}
