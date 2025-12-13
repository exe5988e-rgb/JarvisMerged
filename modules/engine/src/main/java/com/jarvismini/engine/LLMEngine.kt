package com.jarvismini.engine

interface LLMEngine {
    suspend fun generateReply(input: String): EngineResult
}
