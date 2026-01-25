package com.jarvismini.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvismini.core.tts.AssistantTTS

/**
 * Triggered by AlarmManager for missed-task reminders.
 */
class ProgressReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val blockName =
            intent.getStringExtra("blockName") ?: "your task"

        AssistantTTS.speak(
            context.applicationContext,
            "Reminder. You missed $blockName."
        )
    }
}
