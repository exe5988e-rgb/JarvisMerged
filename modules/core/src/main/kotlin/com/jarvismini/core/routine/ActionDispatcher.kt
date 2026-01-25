package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.progress.ProgressConfigLoader
import com.jarvismini.core.progress.QuietHours
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.routine.model.RoutineAction
import com.jarvismini.core.time.NetworkTimeProvider
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.core.notifications.ProgressNotifier
import com.jarvismini.core.MediaController
import com.jarvismini.core.JarvisState
import com.jarvismini.core.JarvisMode
import android.content.Intent
import android.os.Build
import android.app.NotificationManager

/**
 * Dispatches routine actions.
 */
object ActionDispatcher {

    fun dispatch(context: Context, action: RoutineAction) {
        when (action.type) {
            "launch_app" -> action.params["package"]?.let { pkg ->
                launchApp(context, pkg)
            }

            "set_mode" -> action.params["mode"]?.let { modeStr ->
                try {
                    val mode = JarvisMode.valueOf(modeStr)
                    JarvisState.setMode(context, mode)
                } catch (_: Exception) { }
            }

            "set_dnd" -> action.params["dnd"]?.toBoolean()?.let { dnd ->
                setDnd(context, dnd)
            }

            "media_control" -> action.params["media"]?.let { cmd ->
                when(cmd) {
                    "play", "pause" -> MediaController.playPause(context)
                    "next" -> MediaController.next(context)
                    "prev" -> MediaController.previous(context)
                }
            }

            "start_stopwatch" -> startStopwatch(context)
            "start_timer" -> startTimer(context, action.params["duration_min"]?.toIntOrNull() ?: 0)

            "speak" -> action.params["message"]?.let { msg ->
                val config = ProgressConfigLoader.load(context)
                if (!QuietHours.isQuietNow(config)) {
                    AssistantTTS.speak(context, msg)
                }
            }

            "notify" -> action.params["message"]?.let { msg ->
                val blockId = action.params["blockId"] ?: "generic"
                ProgressNotifier.showCompletionPrompt(context, blockId, msg)
            }

            "network_time_sync" -> NetworkTimeProvider.nowMs() // sync time

            else -> android.util.Log.w("ActionDispatcher", "Unknown action type: ${action.type}")
        }
    }

    private fun launchApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }

    private fun setDnd(context: Context, enabled: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == true) {
            nm.setInterruptionFilter(if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                                     else NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    private fun startStopwatch(context: Context) {
        // Launch OnePlus deskclock (stopwatch tab) as before
        launchApp(context, "com.oneplus.deskclock")
    }

    private fun startTimer(context: Context, duration: Int) {
        // Optionally implement timer logic here if needed
        launchApp(context, "com.oneplus.deskclock")
    }
}
