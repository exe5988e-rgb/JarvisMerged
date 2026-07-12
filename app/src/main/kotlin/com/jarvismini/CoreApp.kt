package com.jarvismini

import android.app.Application
import android.content.Context
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.JarvisState
import com.jarvismini.core.routine.TaskTimerManager
import com.jarvismini.ui.timer.FloatingTimerService

class CoreApp : Application() {

    override fun onCreate() {
        super.onCreate()
        JarvisPrefs.init(this)
        JarvisState.init(this)

        // Wake word (Phase 2): auto-seed the Picovoice AccessKey on every
        // process start, so no manual "run once and remove" step is needed.
        // Setting the same value repeatedly is harmless — this just keeps
        // JarvisPrefs("picovoice_access_key") always in sync with this value.
        JarvisPrefs.putString(
            "picovoice_access_key",
            "Z/ixK/Y8Uz8eky9TtaVDPHtJuyWQZCwnsq4kkv1VKEY6gvYpPi+SWA=="
        )

        // Register the floating timer delegate so TaskTimerManager (in :core)
        // can launch FloatingTimerService (in :app) without a Compose dependency.
        TaskTimerManager.floatingTimerDelegate = object : TaskTimerManager.FloatingTimerDelegate {
            override fun startFloating(
                context: Context,
                taskId: String,
                taskName: String,
                totalSeconds: Long
            ) {
                FloatingTimerService.start(context, taskId, taskName, totalSeconds)
            }

            override fun stopFloating(context: Context) {
                FloatingTimerService.stop(context)
            }
        }
    }
}
