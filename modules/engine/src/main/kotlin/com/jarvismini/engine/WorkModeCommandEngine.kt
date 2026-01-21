package com.jarvismini.engine

import android.content.Context
import com.jarvismini.core.WorkModeManager

class WorkModeCommandEngine(
    private val context: Context
) : CommandEngine {

    private val triggerPhrases = listOf(
        "workmode",
        "toggle work",
        "enable work",
        "disable work"
    )

    override fun canHandle(input: String): Boolean {
        val text = input.lowercase()
        return triggerPhrases.any { text.contains(it) }
    }

    override fun handle(input: String): EngineResult {
        if (!canHandle(input)) {
            return EngineResult.Unhandled
        }

        // Core only supports toggle right now
        WorkModeManager.toggle(context)

        return EngineResult.Success("Work mode updated.")
    }
}
