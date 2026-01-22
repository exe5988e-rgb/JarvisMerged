package com.jarvismini.engine

object EngineProvider {

    // Command engines
    private val stubCommandEngine: CommandEngine by lazy { StubCommandEngine() }
    private val workModeCommandEngine: CommandEngine by lazy { WorkModeCommandEngine() }
    private val schedulerCommandEngine: CommandEngine by lazy { SchedulerCommandEngine() }
    private val engines: List<CommandEngine> by lazy {
        listOf(workModeCommandEngine, schedulerCommandEngine, stubCommandEngine)
    }

    // LLM engine
    private val llmEngineInstance: LLMEngine by lazy { StubLLMEngine() }

    /**
     * CommandEngine aggregator
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
