package com.jarvismini.voice

import com.jarvismini.core.announce.AnnouncementEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Routes everything Jarvis says (unprompted announcements and chat replies) through the
 * the selected engine (ElevenLabs / local clone / auto, switchable by chat command), falling
 * back to the phone's built-in voice so Jarvis never goes silent.
 */
object ElevenSpeaker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun install() {
        AnnouncementEngine.speaker = { text, whisper ->
            scope.launch {
                if (!VoiceModule.speakRouted(text)) {
                    AnnouncementEngine.speakSystem(text, whisper)
                }
            }
        }
    }
}
