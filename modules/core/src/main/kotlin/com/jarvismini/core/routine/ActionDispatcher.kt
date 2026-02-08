package com.jarvismini.core.routine

import android.content.Context
import android.content.Intent
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisState
import com.jarvismini.core.Logger
import com.jarvismini.core.WorkModeManager
import com.jarvismini.core.routine.model.RoutineAction
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.core.stopwatch.StopwatchManager

/**
 * Dispatcher for routine actions with full TTS, stopwatch, and mode management support.
 * Uses built-in StopwatchManager - no external clock app needed!
 */
object ActionDispatcher {

    private val TAG = "ActionDispatcher"

    fun dispatch(context: Context, action: RoutineAction) {
        Logger.d(TAG, "Dispatching action: ${action.type}")
        
        when (action.type) {
            "speak" -> handleSpeak(context, action)
            "notify" -> handleNotify(context, action)
            "set_mode" -> handleSetMode(context, action)
            "set_dnd" -> handleSetDnd(context, action)
            "start_stopwatch" -> handleStartStopwatch(context)
            "stop_stopwatch" -> handleStopStopwatch(context)
            "pause_stopwatch" -> handlePauseStopwatch(context)
            "reset_stopwatch" -> handleResetStopwatch(context)
            "network_time_sync" -> handleNetworkTimeSync(context)
            "launch_app" -> handleLaunchApp(context, action)
            else -> Logger.w(TAG, "Unknown action type: ${action.type}")
        }
    }

    /**
     * Handle TTS speak action from routine JSON
     */
    private fun handleSpeak(context: Context, action: RoutineAction) {
        val message = action.params["message"] ?: return
        AssistantTTS.speak(context, message)
        Logger.d(TAG, "TTS spoke: $message")
    }

    /**
     * Handle notification action
     */
    private fun handleNotify(context: Context, action: RoutineAction) {
        val message = action.params["message"] ?: return
        val title = action.params["title"] ?: "Jarvis Routine"
        
        try {
            val notificationIntent = Intent("com.jarvismini.ACTION_ROUTINE_NOTIFY")
            notificationIntent.putExtra("message", message)
            notificationIntent.putExtra("title", title)
            context.sendBroadcast(notificationIntent)
            
            Logger.d(TAG, "Notification sent: $message")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send notification", e)
        }
    }

    /**
     * Handle mode change (WORK, FOCUS -> WORK, NORMAL, SLEEP)
     */
    private fun handleSetMode(context: Context, action: RoutineAction) {
        val modeStr = action.params["mode"] ?: return
        
        try {
            val mode = when (modeStr.uppercase()) {
                "WORK", "FOCUS" -> JarvisMode.WORK
                "NORMAL" -> JarvisMode.NORMAL
                "SLEEP" -> JarvisMode.SLEEP
                else -> JarvisMode.NORMAL
            }
            
            if (mode == JarvisMode.WORK) {
                WorkModeManager.activate(context)
            } else {
                WorkModeManager.deactivate(context)
            }
            
            Logger.d(TAG, "Mode changed to: $mode")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to set mode", e)
        }
    }

    /**
     * Handle Do Not Disturb setting
     */
    private fun handleSetDnd(context: Context, action: RoutineAction) {
        val dnd = action.params["dnd"]?.toBoolean() ?: return
        Logger.d(TAG, "DND setting: $dnd (handled by WorkModeManager)")
    }

    /**
     * Start stopwatch using built-in StopwatchManager
     */
    private fun handleStartStopwatch(context: Context) {
        StopwatchManager.start(context)
        Logger.d(TAG, "Stopwatch started via StopwatchManager")
    }

    /**
     * Stop stopwatch using built-in StopwatchManager
     */
    private fun handleStopStopwatch(context: Context) {
        StopwatchManager.stop(context)
        Logger.d(TAG, "Stopwatch stopped via StopwatchManager")
    }

    /**
     * Pause stopwatch using built-in StopwatchManager
     */
    private fun handlePauseStopwatch(context: Context) {
        StopwatchManager.pause(context)
        Logger.d(TAG, "Stopwatch paused via StopwatchManager")
    }

    /**
     * Reset stopwatch using built-in StopwatchManager
     */
    private fun handleResetStopwatch(context: Context) {
        StopwatchManager.reset(context)
        Logger.d(TAG, "Stopwatch reset via StopwatchManager")
    }

    /**
     * Get elapsed time from StopwatchManager
     */
    fun getElapsedTime(): Long {
        return StopwatchManager.getCurrentElapsed()
    }

    /**
     * Check if stopwatch is running
     */
    fun isStopwatchRunning(): Boolean {
        return StopwatchManager.state.value.isRunning
    }

    /**
     * Handle network time sync
     */
    private fun handleNetworkTimeSync(context: Context) {
        Logger.d(TAG, "Network time sync requested (stub)")
    }

    /**
     * Handle app launch
     */
    private fun handleLaunchApp(context: Context, action: RoutineAction) {
        val packageName = action.params["package"] ?: return
        
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.let { context.startActivity(it) }
            Logger.d(TAG, "Launched app: $packageName")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to launch app: $packageName", e)
        }
    }
}
