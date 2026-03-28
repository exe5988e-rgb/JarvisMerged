package com.jarvismini.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.jarvismini.core.JarvisPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * VoiceModule — Jarvis voice output
 *
 * Priority:
 *   1. ElevenLabs via agent_server TTS proxy (port 8892) — natural voice
 *   2. Android system TTS — fallback if server unreachable
 *
 * Multi-key rotation and voice selection are handled server-side.
 * This class just POSTs to the Termux TTS server.
 */
object VoiceModule {

    private const val TAG       = "VoiceModule"
    private const val TTS_PORT  = 8892
    private const val DEFAULT_HOST = "192.168.29.48"

    private var systemTts: TextToSpeech? = null
    private var systemTtsReady = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        systemTts = TextToSpeech(context) { status ->
            systemTtsReady = (status == TextToSpeech.SUCCESS)
            Log.d(TAG, "System TTS ready: $systemTtsReady")
        }
    }

    fun destroy() {
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
    }

    /**
     * Speak text. Tries ElevenLabs server first, falls back to Android TTS.
     */
    suspend fun speak(
        text:    String,
        voiceId: String? = null,
        mode:    SpeakMode = SpeakMode.AUTO
    ) {
        if (text.isBlank()) return
        when (mode) {
            SpeakMode.ELEVENLABS_ONLY -> speakElevenLabs(text, voiceId)
            SpeakMode.SYSTEM_ONLY     -> speakSystem(text)
            SpeakMode.AUTO -> {
                val ok = speakElevenLabs(text, voiceId)
                if (!ok) speakSystem(text)
            }
        }
    }

    private suspend fun speakElevenLabs(text: String, voiceId: String?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val host = JarvisPrefs.getString("agent_host") ?: DEFAULT_HOST
                val body = JSONObject().apply {
                    put("text", text)
                    if (voiceId != null) put("voice_id", voiceId)
                }.toString().toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("http://$host:$TTS_PORT/speak")
                    .post(body)
                    .build()

                val resp = http.newCall(req).execute()
                val ok   = resp.isSuccessful
                resp.close()
                Log.d(TAG, "ElevenLabs TTS: ${if (ok) "ok" else "failed"}")
                ok
            } catch (e: Exception) {
                Log.w(TAG, "ElevenLabs unreachable: ${e.message}")
                false
            }
        }

    private fun speakSystem(text: String) {
        if (!systemTtsReady) {
            Log.w(TAG, "System TTS not ready")
            return
        }
        systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
        Log.d(TAG, "System TTS: $text")
    }

    enum class SpeakMode { AUTO, ELEVENLABS_ONLY, SYSTEM_ONLY }
}
