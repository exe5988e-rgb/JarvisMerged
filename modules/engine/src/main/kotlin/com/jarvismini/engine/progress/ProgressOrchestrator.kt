package com.jarvismini.engine.progress

import android.content.Context
import com.jarvismini.core.notifications.ProgressNotifier
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.engine.scheduler.DelayedTaskScheduler

class ProgressOrchestrator(
    private val context: Context,
    private val scheduler: DelayedTaskScheduler
) {

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
            ProgressEngine.markIncomplete(context, blockId, blockName)

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
