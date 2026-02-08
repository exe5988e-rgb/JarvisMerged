package com.jarvismini.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build

object WorkModeManager {

    fun toggle(context: Context) {
        if (JarvisState.currentMode == JarvisMode.WORK) {
            deactivate(context)
        } else {
            activate(context)
        }
    }

    fun activate(context: Context) {
        JarvisState.setMode(context, JarvisMode.WORK)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }

        launchApp(context, "xyz.penpencil.physicswala")
        launchApp(context, "com.pittvandewitt.wavelet")
        launchApp(context, "com.google.android.apps.youtube.music")
        launchApp(context, "com.apple.android.music")

        // ⏱ Open OnePlus Clock (user navigates to Stopwatch tab)
        launchApp(context, "com.oneplus.deskclock")

        // Samsung clock (safe)
        launchApp(context, "com.sec.android.app.clockpackage")
    }

    fun deactivate(context: Context) {
        JarvisState.setMode(context, JarvisMode.NORMAL)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }

        // ⏹ Open Clock so Accessibility can click Pause if needed
        launchApp(context, "com.oneplus.deskclock")
    }

    private fun launchApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }
}
