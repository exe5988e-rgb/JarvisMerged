package com.jarvismini.engine

object StubLLMEngine : LLMEngine {

    override fun generateReply(input: String): String {
        return "Stub LLM reply"
    }
}
