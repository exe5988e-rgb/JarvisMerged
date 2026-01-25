package com.jarvismini.engine

/**
 * Fallback command engine.
 * Must NEVER greedily handle commands.
 */
class StubCommandEngine : CommandEngine {

    override fun canHandle(input: String): Boolean {
        return false
    }

    override fun handle(input: String): EngineResult {
        return EngineResult.Unhandled
    }
}
