package com.jarvismini.engine

import android.content.Context

object StubLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // no-op
    }

    override fun generateReply(prompt: String): String {
        return "LLM not available"
    }
}
