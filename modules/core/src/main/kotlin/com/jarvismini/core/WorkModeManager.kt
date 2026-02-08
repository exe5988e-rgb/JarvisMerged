package com.jarvismini.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jarvismini.core.stopwatch.StopwatchManager

object WorkModeManager {

    fun toggle(context: Context) {
        if (JarvisState.currentMode == JarvisMode.WORK) {
            deactivate(context)
        } else {
            activate(context)
        }
    }

    fun enable(context: Context) {
        activate(context)
    }

    fun disable(context: Context) {
        deactivate(context)
    }

    fun activate(context: Context) {
        JarvisState.setMode(context, JarvisMode.WORK)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }

        // Launch productivity apps
        launchApp(context, "xyz.penpencil.physicswala")
        launchApp(context, "com.pittvandewitt.wavelet")
        launchApp(context, "com.google.android.apps.youtube.music")
        launchApp(context, "com.apple.android.music")

        // Auto-start built-in stopwatch when work mode is activated
        StopwatchManager.start(context)
    }

    fun deactivate(context: Context) {
        JarvisState.setMode(context, JarvisMode.NORMAL)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }

        // Stop built-in stopwatch when work mode is deactivated
        StopwatchManager.stop(context)
    }

    private fun launchApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }
}
