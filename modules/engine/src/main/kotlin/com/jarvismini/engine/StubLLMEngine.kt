package com.jarvismini.engine

import android.content.Context

/**
 * ✅ FIXED: Updated to match new suspend interface
 */
object StubLLMEngine : LLMEngine {

    override fun init(context: Context) {
        // No-op for stub
    }

    override suspend fun generateReply(prompt: String): String {
        return "This is Jarvis. Mr. Aamir will respond shortly."
    }
}
