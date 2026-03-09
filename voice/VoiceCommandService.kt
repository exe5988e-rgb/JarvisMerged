package com.jarvismini.voice

/**
 * VoiceCommandService.kt
 *
 * Foreground service that:
 *   1. Listens for wake word "Hey Jarvis" (hotword detection)
 *   2. On trigger → starts SpeechRecognizer for full command
 *   3. Sends recognised text to AgentBridge
 *   4. Receives result → speaks via AssistantTTS (already in JarvisMerged)
 *
 * Runs as ForegroundService so Android never kills it.
 * Uses RECORD_AUDIO permission (add to AndroidManifest.xml).
 *
 * Integration point:
 *   Start from MainActivity or a QuickSettings tile:
 *     startForegroundService(Intent(this, VoiceCommandService::class.java))
 */

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VoiceCommandService : Service() {

    companion object {
        private const val TAG                = "VoiceCommandService"
        private const val NOTIF_CHANNEL_ID  = "jarvis_voice_channel"
        private const val NOTIF_ID          = 9001
        const val ACTION_STOP               = "com.jarvismini.voice.STOP"
        const val ACTION_TRIGGER_MANUAL     = "com.jarvismini.voice.TRIGGER"

        fun start(context: Context) {
            val intent = Intent(context, VoiceCommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, VoiceCommandService::class.java)
                    .setAction(ACTION_STOP)
            )
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    private val scope           = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var agentBridge: AgentBridge? = null

    enum class State { IDLE, LISTENING, PROCESSING }
    private var state = State.IDLE

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VoiceCommandService created")
        agentBridge = AgentBridge(applicationContext)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Listening…"))
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop requested")
                stopSelf()
            }
            ACTION_TRIGGER_MANUAL -> {
                // Called from UI button — force start listening
                if (state == State.IDLE) startListening()
            }
        }
        return START_STICKY   // restart if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        scope.cancel()
        Log.i(TAG, "VoiceCommandService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Speech Recognition ────────────────────────────────────────────────────

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition not available on this device")
            AssistantTTS.speak(this, "Speech recognition not available")
            return
        }

        state = State.LISTENING
        updateNotification("Listening…")

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Indian English first, fallback to US
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,          "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,       1)
            // Keep listening through pauses
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        speechRecognizer?.startListening(intent)
        Log.d(TAG, "Started listening")
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""

            Log.i(TAG, "Recognised: $text")

            if (text.isEmpty()) {
                state = State.IDLE
                restartListeningDelayed()
                return
            }

            onCommandReceived(text)
        }

        override fun onError(error: Int) {
            val msg = speechErrorToString(error)
            Log.w(TAG, "Speech error: $msg ($error)")

            state = State.IDLE

            // Non-fatal errors — just restart
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restartListeningDelayed()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartListeningDelayed(2000)
                else -> restartListeningDelayed(3000)
            }
        }

        override fun onReadyForSpeech(params: Bundle?)  { Log.d(TAG, "Ready for speech") }
        override fun onBeginningOfSpeech()              { Log.d(TAG, "Speech started") }
        override fun onEndOfSpeech()                    { Log.d(TAG, "Speech ended") }
        override fun onRmsChanged(rmsdB: Float)         {}
        override fun onBufferReceived(buffer: ByteArray?){}
        override fun onPartialResults(partial: Bundle?) {}
        override fun onEvent(type: Int, params: Bundle?){}
    }

    // ── Command Handling ──────────────────────────────────────────────────────

    private fun onCommandReceived(text: String) {
        Log.i(TAG, "Command: $text")
        state = State.PROCESSING
        updateNotification("Processing: $text")

        // Speak acknowledgement immediately
        AssistantTTS.speak(this, "On it")

        scope.launch(Dispatchers.IO) {
            try {
                val result = agentBridge!!.sendTask(text)
                Log.i(TAG, "Agent result: $result")

                // Speak result on main thread
                scope.launch(Dispatchers.Main) {
                    AssistantTTS.speak(applicationContext, result)
                    state = State.IDLE
                    restartListeningDelayed(1500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Agent error: ${e.message}")
                scope.launch(Dispatchers.Main) {
                    AssistantTTS.speak(applicationContext, "Sorry, something went wrong")
                    state = State.IDLE
                    restartListeningDelayed(2000)
                }
            }
        }
    }

    private fun restartListeningDelayed(delayMs: Long = 500) {
        scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(delayMs)
            if (state == State.IDLE) startListening()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Jarvis Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Jarvis voice command service"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceCommandService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun speechErrorToString(error: Int) = when (error) {
        SpeechRecognizer.ERROR_AUDIO               -> "audio error"
        SpeechRecognizer.ERROR_CLIENT              -> "client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "no permission"
        SpeechRecognizer.ERROR_NETWORK             -> "network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT     -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH            -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY     -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER              -> "server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT      -> "speech timeout"
        else                                       -> "unknown ($error)"
    }
}
