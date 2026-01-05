package com.jarvismini.engine

lateinit var appContext: android.content.Context

object EngineProvider {
    lateinit var commandEngine: CommandEngine
    val llmEngine: LLMEngine = StubLLMEngine
}
