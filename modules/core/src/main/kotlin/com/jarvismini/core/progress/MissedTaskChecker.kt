package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS

class MissedTaskChecker(private val context: Context) {

    fun checkAndRemind() {
        val missed = ProgressRepository.getAllEntries()
            .filter { it.state == ProgressState.INCOMPLETE && it.missedAt != null }

        missed.forEach { entry ->
            AssistantTTS.speak(
                context.applicationContext,
                "You missed task ${entry.blockId.replace('_', ' ')}"
            )
        }
    }
}
