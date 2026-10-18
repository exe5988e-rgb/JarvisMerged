package com.jarvismini.voice

import com.jarvismini.core.announce.AnnouncementEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Routes everything Jarvis says (unprompted announcements and chat replies) through the
 * ElevenLabs "Jarvis" voice on the Termux TTS server. If that server is unreachable or
 * out of quota, it falls back to the phone's built-in voice so Jarvis never goes silent.
 */
object ElevenSpeaker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun install() {
        AnnouncementEngine.speaker = { text, whisper ->
            scope.launch {
                if (!VoiceModule.speakElevenLabs(text)) {
                    AnnouncementEngine.speakSystem(text, whisper)
                }
            }
        }
    }
}
