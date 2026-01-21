package com.jarvismini.engine

import android.content.Context
import com.jarvismini.engine.commands.SchedulerCommandEngine
import com.jarvismini.engine.commands.WorkModeCommandEngine
import com.jarvismini.engine.stubs.StubCommandEngine
import com.jarvismini.engine.stubs.StubLLMEngine

object EngineProvider {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---------------- COMMAND ENGINES ----------------
    private val stubCommandEngine: CommandEngine by lazy { StubCommandEngine(appContext) }
    private val workModeCommandEngine: CommandEngine by lazy { WorkModeCommandEngine(appContext) }
    private val schedulerCommandEngine: CommandEngine by lazy { SchedulerCommandEngine(appContext) }

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

    // ---------------- LLM ENGINE ----------------
    val llmEngine: LLMEngine by lazy { StubLLMEngine(appContext) }
}
