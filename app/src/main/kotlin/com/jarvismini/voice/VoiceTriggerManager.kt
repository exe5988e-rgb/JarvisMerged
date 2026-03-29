package com.jarvismini.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.jarvismini.core.JarvisPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * VoiceTriggerManager
 *
 * Mode 1 — Orb tap  : call startListening() → one-shot recognition → processTranscript()
 * Mode 2 — Wake word: always-on loop listening for "hey jarvis" / "jarvis"
 *                     → on hit: speak "Yes?" → startListening() for command
 *
 * Transcript → POST /chat on jarvis_conversation server (8893)
 * Response:
 *   CHAT → ElevenLabs speaks reply
 *   TASK → ElevenLabs speaks acknowledgement, agent_server executes task
 */
object VoiceTriggerManager {

    private const val TAG          = "VoiceTrigger"
    private const val CONV_PORT    = 8893
    private const val DEFAULT_HOST = "192.168.29.48"
    private val WAKE_WORDS         = listOf("hey jarvis", "jarvis", "ok jarvis")

    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val http    = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var recognizer:     SpeechRecognizer? = null
    private var wakeRecognizer: SpeechRecognizer? = null
    private var appContext:     Context?           = null
    private var isListeningMain = false
    private var wakeWordEnabled = false

    // ── State ─────────────────────────────────────────────────────────────────

    enum class VoiceState { IDLE, WAKE_LISTENING, ACTIVE_LISTENING, PROCESSING }

    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(context: Context, vm: VoiceModule) {
        appContext = context.applicationContext
        vm.init(context)
    }

    fun destroy() {
        stopWakeWord()
        recognizer?.destroy()
        recognizer = null
        VoiceModule.destroy()
    }

    // ── Orb tap ───────────────────────────────────────────────────────────────

    fun startListening() {
        if (isListeningMain) return
        val ctx = appContext ?: return
        isListeningMain = true
        _state.value    = VoiceState.ACTIVE_LISTENING

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(mainListener)
            startListening(buildIntent(continuous = false))
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        isListeningMain = false
        _state.value    = VoiceState.IDLE
    }

    // ── Wake word ─────────────────────────────────────────────────────────────

    fun startWakeWord() {
        if (wakeWordEnabled) return
        wakeWordEnabled = true
        _startWakeLoop()
    }

    fun stopWakeWord() {
        wakeWordEnabled = false
        wakeRecognizer?.destroy()
        wakeRecognizer  = null
        if (_state.value == VoiceState.WAKE_LISTENING) _state.value = VoiceState.IDLE
    }

    private fun _startWakeLoop() {
        if (!wakeWordEnabled) return
        val ctx = appContext ?: return
        _state.value = VoiceState.WAKE_LISTENING

        wakeRecognizer?.destroy()
        wakeRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(wakeListener)
            startListening(buildIntent(continuous = true))
        }
    }

    // ── Speech intent ─────────────────────────────────────────────────────────

    private fun buildIntent(continuous: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, continuous)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (continuous) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
        }

    // ── Process transcript ────────────────────────────────────────────────────

    private fun processTranscript(text: String) {
        if (text.isBlank()) return
        Log.d(TAG, "Transcript: $text")
        _state.value = VoiceState.PROCESSING

        scope.launch(Dispatchers.IO) {
            try {
                val host = JarvisPrefs.getString("agent_host") ?: DEFAULT_HOST
                val body = JSONObject().apply { put("text", text) }
                    .toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("http://$host:$CONV_PORT/chat")
                    .post(body)
                    .build()

                http.newCall(req).execute().use { resp ->
                    val json  = JSONObject(resp.body?.string() ?: "{}")
                    val reply = json.optString("reply", "")

                    scope.launch(Dispatchers.Main) {
                        _state.value = if (wakeWordEnabled) VoiceState.WAKE_LISTENING else VoiceState.IDLE
                    }

                    if (reply.isNotBlank()) {
                        VoiceModule.speak(reply)
                    }
                }

                if (wakeWordEnabled) handler.postDelayed({ _startWakeLoop() }, 500)

            } catch (e: Exception) {
                Log.e(TAG, "processTranscript failed: ${e.message}")
                scope.launch(Dispatchers.Main) {
                    _state.value = if (wakeWordEnabled) VoiceState.WAKE_LISTENING else VoiceState.IDLE
                }
                VoiceModule.speak("Sorry, I couldn't reach my brain. Check the conversation server.")
                if (wakeWordEnabled) handler.postDelayed({ _startWakeLoop() }, 1000)
            }
        }
    }

    // ── Main listener (orb tap) ───────────────────────────────────────────────

    private val mainListener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            isListeningMain = false
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            processTranscript(text)
        }
        override fun onError(error: Int) {
            isListeningMain = false
            _state.value    = VoiceState.IDLE
            Log.w(TAG, "Main listen error: $error")
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech()         {}
        override fun onRmsChanged(v: Float)        {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech()               {}
        override fun onPartialResults(p: Bundle?)  {}
        override fun onEvent(t: Int, p: Bundle?)   {}
    }

    // ── Wake word listener ────────────────────────────────────────────────────

    private val wakeListener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.lowercase() ?: ""
            Log.d(TAG, "Wake heard: $text")

            if (WAKE_WORDS.any { text.contains(it) }) {
                wakeRecognizer?.destroy()
                wakeRecognizer = null
                _state.value   = VoiceState.ACTIVE_LISTENING
                scope.launch { VoiceModule.speak("Yes?") }
                handler.postDelayed({ startListening() }, 800)
            } else {
                handler.postDelayed({ _startWakeLoop() }, 300)
            }
        }
        override fun onError(error: Int) {
            Log.w(TAG, "Wake error: $error — restarting")
            handler.postDelayed({ _startWakeLoop() }, 500)
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech()         {}
        override fun onRmsChanged(v: Float)        {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech()               {}
        override fun onPartialResults(p: Bundle?)  {}
        override fun onEvent(t: Int, p: Bundle?)   {}
    }
}
