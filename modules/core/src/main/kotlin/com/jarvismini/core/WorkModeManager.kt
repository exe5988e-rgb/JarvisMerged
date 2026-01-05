package com.jarvismini.core

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.*

object WorkModeManager {

    fun activate(context: Context) {
        // 1️⃣ Set mode
        JarvisState.setMode(context, JarvisMode.WORK)

        // 2️⃣ Silence notifications (DND)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        }

        // 3️⃣ Launch apps
        launchApp(context, "xyz.penpencil.physicswala") // Physics Wallah
        launchStopwatch(context)
        launchApp(context, "com.pittvandewitt.wavelet") // Wavelet
        launchApp(context, "com.google.android.apps.youtube.music") // YT Music

        // 4️⃣ Voice announce
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

    private fun speak(context: Context, text: String) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WORK_MODE")
            }
        }

        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                tts.shutdown()
            }
            override fun onError(utteranceId: String?) {}
            override fun onStart(utteranceId: String?) {}
        })
    }
}
