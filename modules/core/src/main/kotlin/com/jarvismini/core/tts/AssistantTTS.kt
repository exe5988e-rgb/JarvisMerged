package com.jarvismini.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object AssistantTTS {

    private var tts: TextToSpeech? = null

    fun speak(context: Context, message: String) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
                }
            }
        } else {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
        }
    }
}
