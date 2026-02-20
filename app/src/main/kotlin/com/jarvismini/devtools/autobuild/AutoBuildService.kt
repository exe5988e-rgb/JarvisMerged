package com.jarvismini.devtools.autobuild

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.jarvismini.devtools.autobuild.models.AutoBuildState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * AccessibilityService entry point for the AutoBuild loop.
 *
 * Enable via: Android Settings → Accessibility → DevTools → AutoBuild Service
 *
 * Static callbacks (onStatusUpdate, onLogLine) are set by MainActivity so the
 * UI receives live updates without binding to the service. They are nullable to
 * avoid memory leaks when MainActivity is destroyed.
 *
 * requestStop() sets a flag read by OrchestrationController on every iteration,
 * allowing a clean exit without killing the coroutine forcibly.
 */
class AutoBuildService : AccessibilityService() {

    companion object {
        private const val TAG = "DevTools:Service"

        /** Called on every state transition: (iteration, state) */
        var onStatusUpdate: ((Int, AutoBuildState) -> Unit)? = null

        /** Called for every log line emitted by the orchestrator: (message, isError) */
        var onLogLine: ((String, Boolean) -> Unit)? = null

        /** Weak reference to the running service instance for stop requests. */
        private var instance: AutoBuildService? = null

        /**
         * Live state snapshot — updated on every loop iteration so MainActivity
         * can display the correct status when it resumes after being in the background.
         */
        @Volatile var currentState: AutoBuildState = AutoBuildState.IDLE
        @Volatile var currentIteration: Int = 0

        /** True only while the loop coroutine is actively running. */
        val isLoopRunning: Boolean get() = instance?.loopJob?.isActive == true

        fun requestStop() {
            instance?.orchestrator?.requestStop()
            Log.d(TAG, "Stop requested")
        }
    }

    private lateinit var uiWatcher:    UIWatcherModule
    private lateinit var orchestrator: OrchestrationController
    private lateinit var notifier:     BuildNotifier

    private val scope = CoroutineScope(Dispatchers.Main)
    private var loopJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance     = this
        uiWatcher    = UIWatcherModule(this)
        orchestrator = OrchestrationController(this, uiWatcher)
        notifier     = BuildNotifier(this)

        // Reset snapshot so stale state from a previous session isn't shown
        currentState     = AutoBuildState.IDLE
        currentIteration = 0

        // Clear the crash-recovery checkpoint so the loop always starts fresh
        // from WAITING_FOR_RESPONSE rather than resuming from a stale state like
        // WAITING_FOR_BUILD (which would poll forever for a flag that never arrives).
        FileManagerModule().clearCheckpoint()

        // Run as foreground service to survive long builds
        startForeground(
            BuildNotifier.NOTIFICATION_ID,
            notifier.buildNotification(0, AutoBuildState.IDLE)
        )

        loopJob = scope.launch {
            try {
                orchestrator.runLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Loop crashed", e)
                notifier.error("Crashed: ${e.message}")
                onLogLine?.invoke("CRASH: ${e.message}", true)
            }
        }

        Log.i(TAG, "AutoBuildService connected and loop started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (::uiWatcher.isInitialized) {
            uiWatcher.onAccessibilityEvent(event.packageName?.toString())
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        loopJob?.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        loopJob?.cancel()
        currentState     = AutoBuildState.IDLE
        currentIteration = 0
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        return super.onUnbind(intent)
    }
}
