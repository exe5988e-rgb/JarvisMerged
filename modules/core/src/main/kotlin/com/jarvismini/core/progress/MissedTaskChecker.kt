package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS

class MissedTaskChecker(private val context: Context) {

    fun checkAndRemind() {
        val registered = ProgressStore.getRegisteredBlocks(context)
        val completed = ProgressStore.getCompletedBlocks(context)

        val missed = registered - completed

        missed.forEach { blockId ->
            AssistantTTS.speak(
                context.applicationContext,
                "You missed task ${blockId.replace('_', ' ')}."
            )
        }
    }
}
