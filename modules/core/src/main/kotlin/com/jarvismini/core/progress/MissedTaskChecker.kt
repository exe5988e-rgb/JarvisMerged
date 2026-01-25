package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS

/**
 * Checks for missed tasks and announces them via TTS.
 * Safe to call from background services or alarms.
 */
class MissedTaskChecker(private val context: Context) {

    fun checkAndRemind() {
        val registered = ProgressStore.getRegisteredBlocks(context)
        val completed = ProgressStore.getCompletedBlocks(context)

        val missed = registered - completed

        missed.forEach { blockId ->
            AssistantTTS.speak(
                context.applicationContext,
                "You missed task $blockId. Please complete it."
            )
        }
    }
}
