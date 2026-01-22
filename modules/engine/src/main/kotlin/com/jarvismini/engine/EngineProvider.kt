package com.jarvismini.engine

import android.content.Context

object EngineProvider {

    private lateinit var appContext: Context

    // Command engines
    private lateinit var workModeCommandEngine: CommandEngine
    private lateinit var schedulerCommandEngine: CommandEngine
    private val stubCommandEngine: CommandEngine = StubCommandEngine
    private lateinit var engines: List<CommandEngine>

    // LLM engine
    private val llmEngineInstance: LLMEngine = StubLLMEngine

    /**
     * Initialize engines that require context
     */
    fun init(context: Context) {
        appContext = context.applicationContext

        workModeCommandEngine = WorkModeCommandEngine(appContext)
        schedulerCommandEngine = SchedulerCommandEngine(appContext)

        engines = listOf(workModeCommandEngine, schedulerCommandEngine, stubCommandEngine)

        // Initialize LLM engine if needed
        llmEngineInstance.init(appContext)
    }

    /**
     * Aggregated CommandEngine
     */
    val commandEngine: CommandEngine
        get() = object : CommandEngine {
            override fun canHandle(input: String): Boolean =
                engines.any { it.canHandle(input) }

            override fun handle(input: String): EngineResult =
                engines.firstOrNull { it.canHandle(input) }?.handle(input)
                    ?: EngineResult.Unhandled
        }

    /**
     * LLM engine accessor
     */
    val llmEngine: LLMEngine
        get() = llmEngineInstance
}
