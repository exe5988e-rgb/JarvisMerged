package com.jarvismini.devtools.autobuild

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.jarvismini.devtools.DevToolsApp
import com.jarvismini.devtools.autobuild.models.AutoBuildState

class BuildNotifier(private val context: Context) {

    companion object {
        const val NOTIFICATION_ID = 9_001
        private const val TAG = "DevTools:Notifier"
    }

    private val nm = context.getSystemService(NotificationManager::class.java)

    fun buildNotification(iteration: Int, state: AutoBuildState): Notification =
        Notification.Builder(context, DevToolsApp.CHANNEL_ID)
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle("AutoBuild — Iter $iteration")
            .setContentText(stateText(state))
            .setOngoing(true)
            .build()

    fun update(iteration: Int, state: AutoBuildState) {
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification(iteration, state)) }
            .onFailure { Log.w(TAG, "Notification update failed", it) }
    }

    fun success(iteration: Int) {
        runCatching {
            nm.notify(NOTIFICATION_ID,
                Notification.Builder(context, DevToolsApp.CHANNEL_ID)
                    .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                    .setContentTitle("✅ Build Succeeded")
                    .setContentText("Done after $iteration iteration(s)")
                    .setAutoCancel(true).build())
        }
    }

    // Session 20: agent loop success notification
    fun agentSuccess() {
        runCatching {
            nm.notify(NOTIFICATION_ID,
                Notification.Builder(context, DevToolsApp.CHANNEL_ID)
                    .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                    .setContentTitle("✅ Agent Task Done")
                    .setContentText("ai-output.txt staged — Phone A can pull now")
                    .setAutoCancel(true).build())
        }
    }

    fun error(msg: String) {
        runCatching {
            nm.notify(NOTIFICATION_ID,
                Notification.Builder(context, DevToolsApp.CHANNEL_ID)
                    .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                    .setContentTitle("❌ AutoBuild Stopped")
                    .setContentText(msg)
                    .setAutoCancel(true).build())
        }
    }

    private fun stateText(state: AutoBuildState) = when (state) {
        AutoBuildState.IDLE                      -> "Idle"
        AutoBuildState.WAITING_FOR_RESPONSE      -> "Waiting for Claude response…"
        AutoBuildState.DOWNLOAD_AI_OUTPUT        -> "Downloading ai-output.txt…"
        AutoBuildState.COPY_TO_AUTOMATION_DIR    -> "Copying to automation dir…"
        AutoBuildState.TRIGGER_BUILD             -> "Triggering build…"
        AutoBuildState.WAITING_FOR_BUILD         -> "Waiting for build…"
        AutoBuildState.BUILD_SUCCEEDED           -> "Build succeeded!"
        AutoBuildState.ATTACHING_ERROR_REPORT    -> "Attaching error report…"
        AutoBuildState.TIMEOUT_ERROR             -> "Timeout — retrying…"
        // Agent loop states
        AutoBuildState.AGENT_IDLE                -> "Agent: idle"
        AutoBuildState.AGENT_SENDING_DUMP        -> "Agent: sending dump to Claude…"
        AutoBuildState.AGENT_WAITING_FOR_RESPONSE -> "Agent: waiting for Claude…"
        AutoBuildState.AGENT_DOWNLOAD_OUTPUT     -> "Agent: downloading output…"
        AutoBuildState.AGENT_STAGING_OUTPUT      -> "Agent: staging for Phone A…"
        AutoBuildState.AGENT_LOOP_DONE           -> "Agent: ✅ done"
    }
}
