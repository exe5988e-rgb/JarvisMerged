package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS

class MissedTaskChecker(private val context: Context) {

    fun checkAndRemind() {
        val entries = ProgressStore.getAllEntries()
        val missedEntries = entries.filter {
            it.state == ProgressState.INCOMPLETE && it.missedAt != null
        }

        missedEntries.forEach { entry ->
            val blockName = entry.blockId.replace('_', ' ')
            AssistantTTS.speak(
                context.applicationContext,
                "You missed task $blockName."
            )
        }
    }
}
