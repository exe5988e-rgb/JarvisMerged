//===== FILE: modules/core/src/main/kotlin/com/jarvismini/core/WorkModeManager.kt =====
package com.jarvismini.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

object WorkModeManager {

    fun activate(context: Context) {
        // 1️⃣ Set mode
        JarvisState.setMode(context, JarvisMode.WORK)

        // 2️⃣ Silence notifications (DND)
        val nm =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(
                    NotificationManager.INTERRUPTION_FILTER_NONE
                )
            }
        }

        // 3️⃣ Launch required apps
        launchApp(context, "xyz.penpencil.physicswala") // Physics Wallah
        launchOnePlusStopwatch(context)                 // ✅ FIXED
        launchApp(context, "com.pittvandewitt.wavelet") // Wavelet
        launchApp(context, "com.google.android.apps.youtube.music") // YT Music
    }

    private fun launchApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // ✅ OnePlus-safe stopwatch launcher
    private fun launchOnePlusStopwatch(context: Context) {
        val intent = context.packageManager
            .getLaunchIntentForPackage("com.oneplus.deskclock")

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(
                context,
                "OnePlus Clock not found",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
