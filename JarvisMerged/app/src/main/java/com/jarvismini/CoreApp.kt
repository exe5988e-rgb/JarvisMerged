package com.jarvismini.core

import android.app.Application
import com.jarvismini.engine.LLMManager

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize AI engine (Puter GPT models)
        LLMManager.init(this)

        // You can initialize other modules here later if needed:
        // SmartManager.init(this)
        // AutomationEngine.init(this)
        // HotwordEngine.init(this)
        // etc.
    }
}