package com.jarvismini.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.*

object WorkModeManager {

    fun activate(context: Context) {
        // 1️⃣ Set mode
        JarvisState.setMode(context, JarvisMode.WORK)

        // 2️⃣ Silence notifications
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }

        // 3️⃣ Launch required apps
        launchApp(context, "xyz.penpencil.physicswala") // Physics Wallah
        launchStopwatch(context)
        launchApp(context, "com.pittvandewitt.wavelet") // Wavelet
        launchYouTubeMusic(context)

        // 4️⃣ Announce
        speak(context, "Grind Mode Initiated")
    }

    private fun launchApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.let { context.startActivity(it) }
    }

    private fun launchStopwatch(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CLOCK)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun launchYouTubeMusic(context: Context) {
        launchApp(context, "com.google.android.apps.youtube.music")
    }

    private fun speak(context: Context, text: String) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WORK_MODE")
            }
        }
    }
}
