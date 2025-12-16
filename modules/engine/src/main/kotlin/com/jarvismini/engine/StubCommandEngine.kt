package com.jarvismini.engine

object StubCommandEngine : CommandEngine {

    override fun canHandle(input: String): Boolean {
        return false
    }

    override fun handle(input: String): EngineResult {
        return EngineResult.Unhandled
    }
}
