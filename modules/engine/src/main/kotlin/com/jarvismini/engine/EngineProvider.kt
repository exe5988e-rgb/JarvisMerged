//===== FILE: modules/engine/src/main/kotlin/com/jarvismini/engine/EngineProvider.kt =====
package com.jarvismini.engine

import android.content.Context

object EngineProvider {

    private lateinit var appContext: Context

    // ---------------- COMMAND ENGINES ----------------

    private lateinit var workModeCommandEngine: CommandEngine
    private lateinit var schedulerCommandEngine: CommandEngine

    // Stub engine MUST be an object
    private val stubCommandEngine: CommandEngine = StubCommandEngine

    private lateinit var engines: List<CommandEngine>

    // ---------------- LLM ENGINE ----------------

    private val llmEngineInstance: LLMEngine = StubLLMEngine

    /**
     * Must be called once from Application.onCreate()
     */
    fun init(context: Context) {
        appContext = context.applicationContext

        workModeCommandEngine = WorkModeCommandEngine(appContext)
        schedulerCommandEngine = SchedulerCommandEngine(appContext)

        engines = listOf(
            workModeCommandEngine,
            schedulerCommandEngine,
            stubCommandEngine
        )

        // Safe init (no-op if stub)
        llmEngineInstance.init(appContext)
    }

    /**
     * Aggregated CommandEngine
     */
    val commandEngine: CommandEngine
        get() {
            check(::engines.isInitialized) {
                "EngineProvider.init(context) was not called"
            }

            return object : CommandEngine {

                override fun canHandle(input: String): Boolean =
                    engines.any { it.canHandle(input) }

                override fun handle(input: String): EngineResult =
                    engines.firstOrNull { it.canHandle(input) }?.handle(input)
                        ?: EngineResult.Unhandled
            }
        }

    /**
     * LLM engine accessor
     */
    val llmEngine: LLMEngine
        get() = llmEngineInstance
}
