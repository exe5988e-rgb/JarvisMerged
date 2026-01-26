package com.jarvismini.engine.progress

import android.content.Context
import com.jarvismini.core.notifications.ProgressNotifier
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.engine.scheduler.DelayedTaskScheduler

/**
 * Orchestrates user prompts and progress tracking.
 */
class ProgressOrchestrator(
    private val context: Context,
    private val scheduler: DelayedTaskScheduler
) {

    /**
     * Prompt the user to complete a task.
     */
    fun prompt(blockId: String, blockName: String) {
        AssistantTTS.speak(
            context,
            "Did you complete $blockName?"
        )

        ProgressNotifier.showCompletionPrompt(
            context = context,
            blockId = blockId,
            blockName = blockName
        )
    }

    /**
     * Handles user response from the prompt.
     */
    fun onUserResponse(
        blockId: String,
        blockName: String,
        completed: Boolean
    ) {
        if (completed) {
            ProgressEngine.markComplete(context, blockId)

            AssistantTTS.speak(
                context,
                "Great. Task marked as complete."
            )
        } else {
            // Mark incomplete and schedule reminder
            ProgressEngine.markIncomplete(context, blockId)

            AssistantTTS.speak(
                context,
                "Okay. I will remind you again in thirty minutes."
            )

            scheduler.schedule(
                delayMs = 30 * 60 * 1000L
            ) {
                prompt(blockId, blockName)
            }
        }
    }
}
