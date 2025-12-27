package com.jarvismini

import android.app.Application
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisState

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ PHASE-2: Restore persisted Jarvis mode
        JarvisState.init(this)

        // ✅ Initialize automation AFTER state is restored
        AutoReplyOrchestrator.init()

        println("CoreApp started with mode: ${JarvisState.currentMode}")
    }
}
