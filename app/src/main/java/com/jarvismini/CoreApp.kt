package com.jarvismini

import android.app.Application
import com.jarvismini.engine.EngineProvider

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize LLM engine (stub or real, selected internally)
        EngineProvider.engine.init(this)
    }
}
