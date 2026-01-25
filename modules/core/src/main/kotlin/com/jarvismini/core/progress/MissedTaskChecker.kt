package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS

class MissedTaskChecker(
    private val context: Context
) {

    fun checkAndRemind() {
        val registered: Set<String> =
            ProgressStore.getRegisteredBlocks(context)

        val completed: Set<String> =
            ProgressStore.getCompletedBlocks(context)

        val missed: Set<String> = registered - completed

        for (blockId in missed) {
            AssistantTTS.speak(
                context.applicationContext,
                "You missed task $blockId. Please complete it."
            )
        }
    }
}
