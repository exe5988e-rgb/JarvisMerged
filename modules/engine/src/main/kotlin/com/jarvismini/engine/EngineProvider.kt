package com.jarvismini.engine

object EngineProvider {

    val commandEngine: CommandEngine = StubCommandEngine
    val llmEngine: LLMEngine = StubLLMEngine
}
