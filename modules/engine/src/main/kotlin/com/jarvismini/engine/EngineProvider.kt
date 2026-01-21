package com.jarvismini.engine

import android.content.Context

object EngineProvider {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // -------------------- COMMAND ENGINES --------------------
    private val workModeCommandEngine by lazy { WorkModeCommandEngine() }
    private val schedulerCommandEngine by lazy { SchedulerCommandEngine(appContext) }
    private val stubCommandEngine by lazy { StubCommandEngine() }

    private val commandEngines: List<CommandEngine> by lazy {
        listOf(workModeCommandEngine, schedulerCommandEngine, stubCommandEngine)
    }

    val commandEngine: CommandEngine
        get() = object : CommandEngine {
            override fun canHandle(input: String): Boolean =
                commandEngines.any { it.canHandle(input) }

            override fun handle(input: String): EngineResult =
                commandEngines.firstOrNull { it.canHandle(input) }?.handle(input)
                    ?: EngineResult.Unhandled
        }

    // -------------------- LLM ENGINE --------------------
    private val stubLLMEngine by lazy { StubLLMEngine() }

    val llmEngine: LLMEngine
        get() = stubLLMEngine
}
