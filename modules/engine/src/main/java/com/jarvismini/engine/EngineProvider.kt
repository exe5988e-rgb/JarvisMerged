package com.jarvismini.engine

object EngineProvider {

    val llm: LLMEngine by lazy {
        StubLLMEngine()
    }

    val command: CommandEngine by lazy {
        StubCommandEngine()
    }
}
