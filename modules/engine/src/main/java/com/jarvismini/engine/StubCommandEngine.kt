package com.jarvismini.engine

class StubCommandEngine : CommandEngine {

    override fun canHandle(input: String): Boolean {
        return false
    }

    override fun handle(input: String): EngineResult {
        return EngineResult.Error("No command handled")
    }
}
