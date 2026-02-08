package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.tts.AssistantTTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ProgressEngine {

    private var isReminderActive = false
    private var reminderJob: Job? = null

    /**
     * Mark a task as complete with TTS feedback
     */
    fun markComplete(context: Context, blockId: String) {
        ProgressRepository.markComplete(context, blockId)
        
        // TTS feedback for task completion
        val config = ProgressConfigLoader.load(context)
        if (config.ttsEnabled) {
            val blockName = blockId.replace('_', ' ').replace('-', ' ')
            AssistantTTS.speak(context, "Task $blockName marked complete. Well done!")
        }
    }

    /**
     * Mark a task as incomplete with TTS feedback
     */
    fun markIncomplete(context: Context, blockId: String) {
        ProgressRepository.markIncomplete(context, blockId)
        
        // TTS feedback for marking incomplete
        val config = ProgressConfigLoader.load(context)
        if (config.ttsEnabled) {
            val blockName = blockId.replace('_', ' ').replace('-', ' ')
            AssistantTTS.speak(context, "Task $blockName marked incomplete. I will remind you later.")
        }
    }

    /**
     * Start periodic reminders for incomplete tasks
     */
    fun startPeriodicReminders(context: Context) {
        if (isReminderActive) return
        
        isReminderActive = true
        reminderJob = CoroutineScope(Dispatchers.Default).launch {
            while (isReminderActive) {
                checkAndRemindIncompleteTasks(context)
                // Check every 30 minutes
                delay(30 * 60 * 1000L)
            }
        }
    }

    /**
     * Stop periodic reminders
     */
    fun stopPeriodicReminders() {
        isReminderActive = false
        reminderJob?.cancel()
        reminderJob = null
    }

    /**
     * Check for incomplete tasks and provide TTS reminders
     */
    private fun checkAndRemindIncompleteTasks(context: Context) {
        val config = ProgressConfigLoader.load(context)
        if (!config.ttsEnabled) return

        // Check if we're in quiet hours
        if (QuietHours.isQuietTime(config.quietStart, config.quietEnd)) {
            return
        }

        val todayEntries = ProgressStore.getTodayEntries()
        val incompleteTasks = todayEntries.filter { it.state == ProgressState.INCOMPLETE }
        
        if (incompleteTasks.isNotEmpty()) {
            val taskNames = incompleteTasks.joinToString(", ") { 
                it.blockId.replace('_', ' ').replace('-', ' ')
            }
            
            val message = when (incompleteTasks.size) {
                1 -> "Reminder: You have 1 incomplete task - $taskNames"
                else -> "Reminder: You have ${incompleteTasks.size} incomplete tasks - $taskNames"
            }
            
            AssistantTTS.speak(context, message)
        }
    }

    /**
     * Provide immediate reminder for a specific incomplete task
     */
    fun remindTask(context: Context, blockId: String) {
        val config = ProgressConfigLoader.load(context)
        if (!config.ttsEnabled) return

        val blockName = blockId.replace('_', ' ').replace('-', ' ')
        AssistantTTS.speak(context, "Remember to complete task: $blockName")
    }

    /**
     * Get summary of today's progress with TTS
     */
    fun speakProgressSummary(context: Context) {
        val stats = ProgressStatsEngine.getStats(context)
        val config = ProgressConfigLoader.load(context)
        
        if (!config.ttsEnabled) return

        val message = buildString {
            append("Progress summary: ")
            append("${stats.completedCount} tasks completed, ")
            append("${stats.pendingCount} pending, ")
            append("${stats.missedCount} missed. ")
            
            if (stats.completionRate > 0) {
                append("Completion rate: ${stats.completionRate.toInt()} percent.")
            }
            
            if (stats.pendingCount > 0) {
                append(" You still have ${stats.pendingCount} tasks to complete today.")
            }
        }
        
        AssistantTTS.speak(context, message)
    }
}
