package com.jarvismini

import android.app.Application
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisState

/**
 * Core Application class for JarvisMini.
 * Initializes global modules and orchestrators.
 */
class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize global app state
        JarvisState.currentMode = JarvisState.defaultMode

        // Initialize automation orchestrator
        AutoReplyOrchestrator.init()

        // Optional: log initialization
        println("CoreApp initialized with mode: ${JarvisState.currentMode}")
    }
}
