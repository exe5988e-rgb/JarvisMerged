package com.jarvismini.engine

import android.content.Intent

sealed class EngineResult {

    data class Success(
        val reply: String
    ) : EngineResult()

    data class LaunchIntent(
        val intent: Intent,
        val reply: String
    ) : EngineResult()

    object Unhandled : EngineResult()
}
