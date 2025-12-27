package com.jarvismini

import android.app.Application
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisState

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        JarvisState.init(this)
        AutoReplyOrchestrator.init()
    }
}
