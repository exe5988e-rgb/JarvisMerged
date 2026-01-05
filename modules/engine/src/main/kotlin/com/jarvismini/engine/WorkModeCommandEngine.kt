package com.jarvismini.engine

import android.content.Context
import com.jarvismini.core.WorkModeManager

class WorkModeCommandEngine(
    private val context: Context
) : CommandEngine {

    override fun canHandle(input: String): Boolean {
        val text = input.lowercase()
        return text.contains("work mode")
                || text.contains("grind mode")
                || text.contains("start working")
    }

    override fun handle(input: String): EngineResult {
        WorkModeManager.activate(context)
        return EngineResult.Success("Work mode activated")
    }
}
