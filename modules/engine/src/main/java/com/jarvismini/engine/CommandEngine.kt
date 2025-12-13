package com.jarvismini.engine

interface CommandEngine {
    fun canHandle(input: String): Boolean
    fun handle(input: String): EngineResult
}
