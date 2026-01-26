package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS
import java.util.Calendar

class MissedTaskChecker(private val context: Context) {

    fun checkAndRemind() {
        // Get today's entries
        val todayEntries = ProgressRepository.getTodayEntries()

        // Filter for incomplete tasks (explicit type needed)
        val missedEntries = todayEntries.filter { entry: ProgressEntry ->
            entry.state == ProgressState.INCOMPLETE
        }

        missedEntries.forEach { entry ->
            val blockName = entry.blockId.replace('_', ' ')
            AssistantTTS.speak(
                context.applicationContext,
                "Reminder: you missed task $blockName."
            )
        }
    }

    /**
     * Optional helper if you want to auto-schedule a 30-min reminder
     */
    fun remindLater(entry: ProgressEntry) {
        AssistantTTS.speak(
            context.applicationContext,
            "Okay, I will remind you in 30 minutes."
        )
        // Here you can add actual scheduling logic if needed
    }
}
