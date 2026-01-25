package com.jarvismini

import android.app.Application
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.JarvisState
import com.jarvismini.core.TimeAnchorManager
import com.jarvismini.core.progress.AppContextProvider

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ Global application context for background engines
        AppContextProvider.appContext = applicationContext

        JarvisPrefs.init(this)
        TimeAnchorManager.init()
        JarvisState.init(this)
        AutoReplyOrchestrator.init()
    }
}
