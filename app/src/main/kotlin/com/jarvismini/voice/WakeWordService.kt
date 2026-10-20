package com.jarvismini.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.announce.AnnouncementEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * WakeWordService (Phase 2)
 *
 * Always-on foreground service that listens for the "Jarvis" wake word
 * offline via sherpa-onnx keyword spotting (KwsWakeWord). On detection it pauses wake-word listening,
 * hands off to VoiceTriggerManager for a single command capture (STT ->
 * Intent Router path), then resumes listening once that round-trip
 * completes or errors out.
 *
 * Started from MainActivity.startWakeWordServiceIfPermitted() once
 * RECORD_AUDIO is granted. Declared in AndroidManifest.xml with
 * foregroundServiceType="microphone".
 */
class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "wake_word_channel"
        private const val NOTIF_ID = 8893


        /** After a reply, Jarvis reopens the mic by itself (no wake word) up to this many times in a row. */
        private const val MAX_FOLLOW_UPS = 3

        /** Lets the proactive engine open the mic right after Jarvis asks a question. Null when the service is not running. */
        @Volatile var listenNowHook: (() -> Unit)? = null
    }

    private var porcupineManager: KwsWakeWord? = null
    private var voiceTriggerManager: VoiceTriggerManager? = null

    private val svcScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var followUps = 0
    /** Bumped on every user-initiated turn so a stale pending follow-up never opens the mic. */
    private var epoch = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        listenNowHook = {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                epoch++; followUps = 0
                try { porcupineManager?.stop() } catch (_: Exception) {}
                listenForCommand()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        startPorcupine()
        return START_STICKY
    }

    override fun onDestroy() {
        listenNowHook = null
        svcScope.cancel()
        stopListeningForCommand()
        stopPorcupine()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Porcupine lifecycle ──────────────────────────────────────────────

    private fun startPorcupine() {
        if (porcupineManager != null) return
        val kws = KwsWakeWord(applicationContext) { onWakeWord() }
        if (!kws.start()) {
            Log.e(TAG, "Wake word engine failed to start — stopping")
            stopSelf()
            return
        }
        porcupineManager = kws
        Log.i(TAG, "Wake word detection started")
    }

    private fun stopPorcupine() {
        try { porcupineManager?.delete() } catch (e: Exception) { Log.w(TAG, "Error stopping wake word: ${e.message}") }
        porcupineManager = null
    }

    private fun onWakeWord() {
        Log.i(TAG, "Wake word detected")
        // The detector already released the mic; hop to the main thread for the rest.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Barge-in: saying the wake word while Jarvis is talking cuts him off.
            epoch++; followUps = 0
            svcScope.launch { VoiceModule.stopSpeaking() }
            listenForCommand()
        }, 150)  // brief gap so the detector's mic is fully released first
    }

    // ── Command capture handoff ──────────────────────────────────────────

    private fun listenForCommand() {
        stopListeningForCommand()

        // Keep Jarvis from announcing over the user while they speak a command.
        AnnouncementEngine.userInteracting = true

        val vtm = VoiceTriggerManager(applicationContext)
        voiceTriggerManager = vtm
        vtm.start(
            onReady = { Log.d(TAG, "Listening for command...") },
            onResult = { text ->
                Log.d(TAG, "Command captured: $text")
                stopListeningForCommand()
                // Wake word stays live while Jarvis answers (barge-in); then reopen the mic for a follow-up.
                resumePorcupine()
                if (followUps < MAX_FOLLOW_UPS) {
                    followUps++
                    scheduleFollowUp(text, epoch)
                } else {
                    followUps = 0
                }
            },
            onError = { msg ->
                Log.w(TAG, "Voice trigger error: $msg")
                followUps = 0  // silence / no match ends the conversation
                stopListeningForCommand()
                resumePorcupine()
            }
        )
    }

    /** Waits for Jarvis to finish speaking, then reopens the mic without needing the wake word. */
    private fun scheduleFollowUp(replyText: String, myEpoch: Int) {
        svcScope.launch {
            delay(1500)  // let the reply reach the voice server's queue
            val deadline = System.currentTimeMillis() + 40_000
            if (VoiceModule.isSpeaking() == null) {
                delay((replyText.length * 65L).coerceIn(500L, 15_000L))  // server unreachable: estimate
            } else {
                while (System.currentTimeMillis() < deadline && VoiceModule.isSpeaking() == true) delay(300)
            }
            delay(500)  // tail so the mic doesn't catch the end of his voice
            if (myEpoch != epoch) return@launch
            try { porcupineManager?.stop() } catch (_: Exception) {}
            listenForCommand()
        }
    }

    private fun stopListeningForCommand() {
        AnnouncementEngine.userInteracting = false
        voiceTriggerManager?.destroy()
        voiceTriggerManager = null
    }

    private fun resumePorcupine() {
        try {
            porcupineManager?.start()
        } catch (e: Exception) {
            Log.w(TAG, "Error resuming Porcupine: ${e.message}")
        }
    }

    // ── Foreground notification ──────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Listening",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Jarvis is listening for the wake word"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis")
            .setContentText("Listening for wake word")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
}
