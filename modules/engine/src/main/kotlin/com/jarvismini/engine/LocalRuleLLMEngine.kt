package com.jarvismini.engine

import android.content.Context

object LocalRuleLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // Local-only engine, no init needed
    }

    override fun generateReply(prompt: String): String {
        return "Processing: $prompt"
    }
}
