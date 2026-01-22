package com.jarvismini.engine

class StubCommandEngine : CommandEngine {

    override fun canHandle(input: String): Boolean = false

    override fun handle(input: String): EngineResult =
        EngineResult.Unhandled
}
