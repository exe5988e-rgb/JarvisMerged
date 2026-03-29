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
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * VoiceTriggerManager — class (one instance per screen lifecycle)
 *
 * Usage:
 *   val vtm = remember { VoiceTriggerManager(context) }
 *   DisposableEffect(Unit) { onDispose { vtm.destroy() } }
 *
 *   vtm.start(
 *     onReady  = { /* mic open */ },
 *     onResult = { transcript -> /* do something with text */ },
 *     onError  = { msg -> /* show error */ }
 *   )
 *   vtm.stop()
 *
 * onResult receives the full Jarvis reply text (CHAT) or acknowledgement (TASK).
 * For TASK responses, the agent_server dispatch is handled server-side by jarvis_conversation.py.
 */
class VoiceTriggerManager(private val context: Context) {

    private val TAG          = "VoiceTrigger"
    private val CONV_PORT    = 8893
    private val DEFAULT_HOST = "192.168.29.48"

    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val http    = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var recognizer: SpeechRecognizer? = null
    private var onReadyCb:  (() -> Unit)?     = null
    private var onResultCb: ((String) -> Unit)? = null
    private var onErrorCb:  ((String) -> Unit)? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun start(
        onReady:  () -> Unit,
        onResult: (String) -> Unit,
        onError:  (String) -> Unit,
    ) {
        onReadyCb  = onReady
        onResultCb = onResult
        onErrorCb  = onError

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
            )
        }
    }

    fun stop() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() {
        stop()
    }

    // ── Process transcript → conversation server ───────────────────────────────

    private fun processTranscript(text: String) {
        if (text.isBlank()) {
            onErrorCb?.invoke("Didn't catch that")
            return
        }
        Log.d(TAG, "Transcript: $text")

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
                    val type  = json.optString("type", "CHAT")
                    Log.d(TAG, "type=$type reply=$reply")

                    // Speak the reply via ElevenLabs/system TTS
                    if (reply.isNotBlank()) {
                        VoiceModule.speak(reply)
                    }

                    // Deliver result back to UI on main thread
                    handler.post {
                        if (type == "TASK") {
                            // Pass task description so UI can navigate to AgentDashboard
                            onResultCb?.invoke(json.optString("task", text))
                        } else {
                            // CHAT — deliver reply text so UI can show it
                            onResultCb?.invoke(reply)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "processTranscript failed: ${e.message}")
                val errMsg = "Couldn't reach conversation server"
                VoiceModule.speak(errMsg)
                handler.post { onErrorCb?.invoke(errMsg) }
            }
        }
    }

    // ── Recognition listener ──────────────────────────────────────────────────

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            handler.post { onReadyCb?.invoke() }
        }
        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            processTranscript(text)
        }
        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH        -> "No speech detected"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT  -> "Listening timed out"
                SpeechRecognizer.ERROR_NETWORK         -> "Network error"
                SpeechRecognizer.ERROR_AUDIO           -> "Audio error"
                else                                   -> "Error $error"
            }
            Log.w(TAG, "Recognition error: $msg")
            handler.post { onErrorCb?.invoke(msg) }
        }
        override fun onBeginningOfSpeech()                {}
        override fun onRmsChanged(v: Float)               {}
        override fun onBufferReceived(b: ByteArray?)      {}
        override fun onEndOfSpeech()                      {}
        override fun onPartialResults(p: Bundle?)         {}
        override fun onEvent(t: Int, p: Bundle?)          {}
    }
}
