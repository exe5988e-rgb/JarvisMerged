package com.jarvismini.voice

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
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
 * Always-on foreground service that listens for the "porcupine_jarvis.ppn"
 * wake word via Porcupine. On detection it pauses wake-word listening,
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

        // Relative to app/src/main/assets/ — Porcupine's Android SDK resolves
        // and copies asset-relative keyword paths internally.
        private const val KEYWORD_ASSET_PATH = "porcupine_jarvis.ppn"
        private const val SENSITIVITY = 0.6f

        /** After a reply, Jarvis reopens the mic by itself (no wake word) up to this many times in a row. */
        private const val MAX_FOLLOW_UPS = 3

        /** Lets the proactive engine open the mic right after Jarvis asks a question. Null when the service is not running. */
        @Volatile var listenNowHook: (() -> Unit)? = null
    }

    private var porcupineManager: PorcupineManager? = null
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

        val accessKey = JarvisPrefs.getString("picovoice_access_key")
        if (accessKey.isNullOrBlank()) {
            Log.e(TAG, "No Picovoice access key in JarvisPrefs — stopping")
            stopSelf()
            return
        }

        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(KEYWORD_ASSET_PATH)
                .setSensitivity(SENSITIVITY)
                .build(applicationContext, wakeWordCallback)
            porcupineManager?.start()
            Log.i(TAG, "Wake word detection started")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to start Porcupine: ${e.message}", e)
            stopSelf()
        }
    }

    private fun stopPorcupine() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.w(TAG, "Error stopping Porcupine: ${e.message}")
        }
        porcupineManager = null
    }

    private val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
        Log.i(TAG, "Wake word detected (index=$keywordIndex)")
        // Barge-in: saying the wake word while Jarvis is talking cuts him off.
        epoch++; followUps = 0
        svcScope.launch { VoiceModule.stopSpeaking() }
        // Pause wake-word listening while capturing the follow-up command so
        // the mic isn't shared between two recognizers at once.
        try {
            porcupineManager?.stop()
        } catch (e: PorcupineException) {
            Log.w(TAG, "Error pausing Porcupine after detection: ${e.message}")
        }
        listenForCommand()
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
        } catch (e: PorcupineException) {
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
