package com.jarvismini.engine

import android.content.Context

object EngineProvider {

    private val engines = mutableListOf<CommandEngine>()

    fun init(context: Context) {
        engines.clear()

        engines += WorkModeCommandEngine(context.applicationContext)
        engines += SchedulerCommandEngine(context.applicationContext)
    }

    fun handle(input: String): EngineResult {
        for (engine in engines) {
            if (engine.canHandle(input)) {
                val result = engine.handle(input)
                if (result !is EngineResult.Unhandled) return result
            }
        }
        return EngineResult.Unhandled
    }
}
