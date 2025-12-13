package com.jarvismini.engine

class StubLLMEngine : LLMEngine {

    override suspend fun generateReply(input: String): EngineResult {
        return EngineResult.Success("Stub reply for: $input")
    }
}
