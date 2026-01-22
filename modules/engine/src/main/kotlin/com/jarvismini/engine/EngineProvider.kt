package com.jarvismini.engine

import android.content.Context
// Correct imports based on your repo tree
import com.jarvismini.engine.SchedulerCommandEngine
import com.jarvismini.engine.WorkModeCommandEngine
import com.jarvismini.engine.StubCommandEngine
import com.jarvismini.engine.StubLLMEngine

object EngineProvider {

    private lateinit var appContext: Context

    // ---------------- COMMAND ENGINES ----------------
    private lateinit var stubCommandEngine: CommandEngine
    private lateinit var workModeCommandEngine: CommandEngine
    private lateinit var schedulerCommandEngine: CommandEngine
    private lateinit var engines: List<CommandEngine>

    // ---------------- LLM ENGINE ----------------
    private lateinit var llmEngineInstance: LLMEngine

    fun init(context: Context) {
        appContext = context.applicationContext

        // Initialize command engines
        stubCommandEngine = StubCommandEngine(appContext)
        workModeCommandEngine = WorkModeCommandEngine(appContext)
        schedulerCommandEngine = SchedulerCommandEngine(appContext)

        engines = listOf(workModeCommandEngine, schedulerCommandEngine, stubCommandEngine)

        // Initialize LLM engine
        llmEngineInstance = StubLLMEngine(appContext)
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

    val llmEngine: LLMEngine
        get() = llmEngineInstance
}
