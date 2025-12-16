package com.jarvismini.engine

import android.content.Context

object StubLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // No-op for stub
    }

    override fun generateReply(prompt: String): String {
        return "This is Jarvis. Mr. Aamir will respond shortly."
    }
}
