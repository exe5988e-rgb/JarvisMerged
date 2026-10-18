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
import java.util.Locale
import java.util.concurrent.TimeUnit

object VoiceModule {

    private const val TAG          = "VoiceModule"
    private const val TTS_PORT     = 8892
    private const val DEFAULT_HOST = "192.168.29.48"

    private var systemTts:      TextToSpeech? = null
    private var systemTtsReady: Boolean       = false

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        systemTts = TextToSpeech(context) { status ->
            systemTtsReady = (status == TextToSpeech.SUCCESS)
            if (systemTtsReady) systemTts?.language = Locale.ENGLISH
            Log.d(TAG, "System TTS ready: $systemTtsReady")
        }
    }

    fun destroy() {
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
    }

    suspend fun speak(
        text:    String,
        voiceId: String?   = null,
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

    /** Asks the Termux ElevenLabs server (custom Jarvis voice) to speak. True if it accepted the request. */
    suspend fun speakElevenLabs(text: String, voiceId: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val configured = JarvisPrefs.getString("agent_host") ?: DEFAULT_HOST
            // The TTS server usually runs on this same phone, so try localhost if the LAN address fails.
            for (host in listOf(configured, "127.0.0.1").distinct()) {
                try {
                    val body = JSONObject().apply {
                        put("text", text)
                        if (voiceId != null) put("voice_id", voiceId)
                    }.toString().toRequestBody("application/json".toMediaType())

                    val req = Request.Builder()
                        .url("http://$host:$TTS_PORT/speak")
                        .post(body)
                        .build()

                    http.newCall(req).execute().use { if (it.isSuccessful) return@withContext true }
                } catch (e: Exception) {
                    Log.w(TAG, "ElevenLabs via $host failed: ${e.message}")
                }
            }
            false
        }

    private fun speakSystem(text: String) {
        if (!systemTtsReady) { Log.w(TAG, "System TTS not ready"); return }
        systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j_${System.currentTimeMillis()}")
    }
}

enum class SpeakMode { AUTO, ELEVENLABS_ONLY, SYSTEM_ONLY }
