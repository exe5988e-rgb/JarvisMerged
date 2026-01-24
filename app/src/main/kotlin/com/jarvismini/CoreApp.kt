package com.jarvismini

import android.app.Application
import com.jarvismini.automation.orchestrator.AutoReplyOrchestrator
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.JarvisState
import com.jarvismini.core.TimeAnchorManager

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()

        JarvisPrefs.init(this)
        TimeAnchorManager.init()

        JarvisState.init(this)
        AutoReplyOrchestrator.init()
    }
}
