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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoBuildService : AccessibilityService() {

    companion object {
        private const val TAG = "DevTools:Service"

        var onStatusUpdate: ((Int, AutoBuildState) -> Unit)? = null
        var onLogLine: ((String, Boolean) -> Unit)? = null

        private var instance: AutoBuildService? = null

        @Volatile var currentState: AutoBuildState = AutoBuildState.IDLE
        @Volatile var currentIteration: Int = 0

        val isLoopRunning: Boolean get() = instance?.loopJob?.isActive == true

        fun requestStop() {
            instance?.orchestrator?.requestStop()
            Log.d(TAG, "Stop requested")
        }
    }

    private lateinit var uiWatcher:   UIWatcherModule
    private lateinit var fileManager: FileManagerModule
    private lateinit var termuxBridge: TermuxBridgeModule
    private lateinit var orchestrator: OrchestrationController
    private lateinit var notifier:    BuildNotifier

    private val scope   = CoroutineScope(Dispatchers.Main)
    private var loopJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        uiWatcher    = UIWatcherModule(this)
        fileManager  = FileManagerModule(this)
        termuxBridge = TermuxBridgeModule(this)
        notifier     = BuildNotifier(this)
        orchestrator = OrchestrationController(this, uiWatcher, fileManager, termuxBridge, notifier)

        currentState     = AutoBuildState.IDLE
        currentIteration = 0

        startForeground(BuildNotifier.NOTIFICATION_ID, notifier.buildNotification(0, AutoBuildState.IDLE))

        loopJob = scope.launch {
            // ── Session 20: poll for agent_task.txt before running autobuild loop ──
            // If agent_task.txt exists when the service starts, run the agent loop.
            // Otherwise run the standard autobuild loop as before.
            try {
                val agentTask = fileManager.readAgentTask()
                if (agentTask != null) {
                    val (task, dumpName) = agentTask
                    onLogLine?.invoke("[agent] Task detected: $task", false)
                    fileManager.deleteAgentTask()   // consume so it doesn't re-trigger on restart
                    orchestrator.runAgentLoop(task, dumpName)
                } else {
                    orchestrator.runLoop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Loop crashed", e)
                notifier.error("Crashed: ${e.message}")
                onLogLine?.invoke("CRASH: ${e.message}", true)
            }
        }

        Log.i(TAG, "AutoBuildService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

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
