package com.jarvismini.engine

import android.content.Context

object EngineProvider {

    lateinit var commandEngine: CommandEngine
        private set

    val llmEngine: LLMEngine = StubLLMEngine

    fun init(context: Context) {
        commandEngine = WorkModeCommandEngine(context.applicationContext)
        llmEngine.init(context.applicationContext)
    }
}
