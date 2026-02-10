package com.jarvismini

import android.app.Application
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.JarvisState
import com.jarvismini.core.TimeAnchorManager
import com.jarvismini.core.progress.AppContextProvider
import com.jarvismini.engine.ai.LlamaNative

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ Global application context for background engines
        AppContextProvider.appContext = applicationContext

        JarvisPrefs.init(this)
        TimeAnchorManager.init()
        JarvisState.init(this)
        AutoReplyOrchestrator.init()
        
        // ✅ CRITICAL: Initialize llama backend ONCE here
        LlamaNative.initBackend()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // ✅ Clean up backend on app termination
        LlamaNative.freeBackend()
    }
}
