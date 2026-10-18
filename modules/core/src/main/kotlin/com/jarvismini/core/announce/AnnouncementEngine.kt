package com.jarvismini.core.announce

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.jarvismini.core.JarvisMode
import com.jarvismini.core.JarvisPersona
import com.jarvismini.core.JarvisPrefs
import com.jarvismini.core.JarvisState
import java.util.Locale

enum class AnnouncePriority { LOW, NORMAL, HIGH, CRITICAL }

/**
 * Single gate for everything Jarvis says WITHOUT being asked.
 *
 * Callers log the underlying event to EventLog first, then call announce().
 * If the gate holds an announcement back, nothing is lost: the event is still
 * in the log and shows up in the next digest ("what did I miss", wake-up
 * summary). Replies to something the user just asked use respond() instead.
 */
object AnnouncementEngine {

    private const val TAG = "Announce"
    private const val KEY_ENABLED = "announce_enabled"
    private const val DEDUPE_MS = 5 * 60_000L
    private const val HOUR_MS = 60 * 60_000L
    private const val MAX_NORMAL_PER_HOUR = 6

    /** Optional app-level voice (e.g. ElevenLabs). Falls back to system TTS when null. */
    var speaker: ((text: String, whisper: Boolean) -> Unit)? = null

    /** Set true while the user is mid-command (wake word capture) so Jarvis does not talk over them. */
    @Volatile
    var userInteracting: Boolean = false

    private var appContext: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pending = ArrayList<Pair<String, Boolean>>()
    private val lastSpoken = HashMap<String, Long>()
    private val recentNormal = ArrayDeque<Long>()
    private var focusRequest: AudioFocusRequest? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isEnabled(): Boolean = JarvisPrefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(on: Boolean) = JarvisPrefs.putBoolean(KEY_ENABLED, on)

    /** Unprompted speech. Returns true if it was spoken, false if held back. */
    @Synchronized
    fun announce(
        text: String,
        priority: AnnouncePriority = AnnouncePriority.NORMAL,
        key: String = text
    ): Boolean {
        if (text.isBlank()) return false
        val now = System.currentTimeMillis()
        if (!allowed(priority, key, now)) {
            Log.d(TAG, "held [$priority] $key")
            return false
        }
        lastSpoken[key] = now
        if (priority == AnnouncePriority.NORMAL) recentNormal.addLast(now)
        speak(text)
        return true
    }

    /** Reply to something the user just asked for. Skips quiet-mode rules, never talks over a call. */
    @Synchronized
    fun respond(text: String) {
        if (text.isBlank() || inCall()) return
        speak(text)
    }

    private fun allowed(p: AnnouncePriority, key: String, now: Long): Boolean {
        if (inCall()) return false
        if (!isEnabled() && p != AnnouncePriority.CRITICAL) return false
        if (userInteracting && p != AnnouncePriority.CRITICAL) return false
        if (p == AnnouncePriority.LOW) return false // digest only
        if (p < minPriority()) return false

        val last = lastSpoken[key]
        if (last != null && now - last < DEDUPE_MS) return false

        if (p == AnnouncePriority.NORMAL) {
            while (recentNormal.isNotEmpty() && now - recentNormal.first() > HOUR_MS) {
                recentNormal.removeFirst()
            }
            if (recentNormal.size >= MAX_NORMAL_PER_HOUR) return false
        }
        return true
    }

    private fun minPriority(): AnnouncePriority {
        val byMode = when (JarvisState.currentMode) {
            JarvisMode.SLEEP -> AnnouncePriority.CRITICAL
            JarvisMode.FOCUS, JarvisMode.WORK -> AnnouncePriority.HIGH
            else -> AnnouncePriority.NORMAL
        }
        val byPersona = when (JarvisPersona.current()) {
            JarvisPersona.SILENT_GUARDIAN -> AnnouncePriority.CRITICAL
            JarvisPersona.STUDY -> AnnouncePriority.HIGH
            else -> AnnouncePriority.NORMAL
        }
        return if (byMode >= byPersona) byMode else byPersona
    }

    private fun whisper(): Boolean =
        JarvisPersona.current() == JarvisPersona.CALM_WHISPER ||
            JarvisState.currentMode == JarvisMode.SLEEP

    private fun inCall(): Boolean {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val m = am.mode
        return m == AudioManager.MODE_IN_CALL ||
            m == AudioManager.MODE_IN_COMMUNICATION ||
            m == AudioManager.MODE_RINGTONE
    }

    private fun speak(text: String) {
        val w = whisper()
        val custom = speaker
        if (custom != null) {
            custom(text, w)
        } else {
            speakSystem(text, w)
        }
    }

    private fun speakSystem(text: String, whisper: Boolean) {
        val ctx = appContext ?: return
        if (tts == null) {
            tts = TextToSpeech(ctx) { status ->
                synchronized(this) {
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        tts?.language = Locale.ENGLISH
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) { releaseFocus() }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) { releaseFocus() }
                        })
                        pending.forEach { (t, w) -> play(t, w) }
                    }
                    pending.clear()
                }
            }
        }
        if (ttsReady) play(text, whisper) else pending.add(text to whisper)
    }

    private fun play(text: String, whisper: Boolean) {
        val engine = tts ?: return
        requestFocus()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (whisper) 0.35f else 1.0f)
        }
        engine.setSpeechRate(if (whisper) 0.9f else 1.0f)
        engine.setPitch(if (whisper) 0.9f else 1.0f)
        engine.speak(text, TextToSpeech.QUEUE_ADD, params, "ann_${System.nanoTime()}")
    }

    private fun requestFocus() {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun releaseFocus() {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }
}
