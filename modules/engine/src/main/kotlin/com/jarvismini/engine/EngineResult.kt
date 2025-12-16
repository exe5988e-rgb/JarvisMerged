package com.jarvismini.engine

sealed class EngineResult {

    data class Success(
        val reply: String
    ) : EngineResult()

    object Unhandled : EngineResult()
}
