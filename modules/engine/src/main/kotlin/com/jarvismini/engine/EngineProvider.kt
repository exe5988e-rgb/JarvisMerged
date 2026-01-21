package com.jarvismini.engine

import android.content.Context

object EngineProvider {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---------------- COMMAND ENGINES ----------------
    private val stubCommandEngine by lazy { StubCommandEngine() }
    private val workModeCommandEngine by lazy { WorkModeCommandEngine() }
    private val schedulerCommandEngine by lazy { SchedulerCommandEngine(appContext) }

    private val engines: List<CommandEngine> by lazy {
        listOf(workModeCommandEngine, schedulerCommandEngine, stubCommandEngine)
    }

    // ---------------- PUBLIC ACCESS ----------------
    val commandEngine: CommandEngine
        get() = object : CommandEngine {
            override fun canHandle(input: String): Boolean =
                engines.any { it.canHandle(input) }

            override fun handle(input: String): EngineResult =
                engines.firstOrNull { it.canHandle(input) }?.handle(input)
                    ?: EngineResult.Unhandled
        }

    val llmEngine: LLMEngine by lazy { StubLLMEngine() }
}
