package com.jarvismini.engine

sealed class EngineResult {
    data class Success(val output: String) : EngineResult()
    data class Error(val reason: String) : EngineResult()
}
