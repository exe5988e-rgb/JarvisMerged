//===== FILE: modules/core/src/main/kotlin/com/jarvismini/core/WorkModeManager.kt =====
package com.jarvismini.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build

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

        // 3️⃣ Launch required apps (SAFE)
        launchApp(context, "xyz.penpencil.physicswala")      // Physics Wallah
        launchApp(context, "com.oneplus.deskclock")          // OnePlus Clock
        launchApp(context, "com.pittvandewitt.wavelet")      // Wavelet
        launchApp(context, "com.google.android.apps.youtube.music") // YT Music
    }

    private fun launchApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }
}
