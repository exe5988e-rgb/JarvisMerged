package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS

class MissedTaskChecker(private val context: Context) {

    fun checkAndRemind() {
        // ProgressRepository does NOT expose getTodayEntries
        // ProgressStore does (and already exists in repo)
        val todayEntries = ProgressStore.getTodayEntries()

        todayEntries
            .filter { it.state == ProgressState.INCOMPLETE }
            .forEach { entry ->
                val blockName = entry.blockId.replace('_', ' ')
                AssistantTTS.speak(
                    context.applicationContext,
                    "Reminder: you missed task $blockName."
                )
            }
    }

    fun remindLater(entry: ProgressEntry) {
        AssistantTTS.speak(
            context.applicationContext,
            "Okay, I will remind you in 30 minutes."
        )
    }
}
