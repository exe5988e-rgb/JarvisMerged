package com.jarvismini.engine.progress

import android.content.Context
import com.jarvismini.core.progress.ProgressState
import com.jarvismini.core.progress.ProgressStore
import com.jarvismini.core.TimeUtils
import com.jarvismini.engine.scheduler.DelayedTaskScheduler
import com.jarvismini.tts.TtsManager
import com.jarvismini.notifications.NotificationManager

class ProgressOrchestrator(
    private val context: Context,
    private val scheduler: DelayedTaskScheduler,
    private val tts: TtsManager,
    private val notifier: NotificationManager
) {

    private val store = ProgressStore(context)

    fun prompt(routineId: String, blockId: String) {
        tts.speak("Did you complete this task?")
        notifier.showProgressPrompt(
            routineId = routineId,
            blockId = blockId
        )
    }

    fun onUserResponse(
        routineId: String,
        blockId: String,
        completed: Boolean
    ) {
        if (completed) {
            store.saveProgress(routineId, blockId, ProgressState.COMPLETED)
            tts.speak("Great. Task marked as complete.")
        } else {
            store.saveProgress(routineId, blockId, ProgressState.INCOMPLETE)
            tts.speak("Okay. I will remind you again in thirty minutes.")

            scheduler.schedule(
                delayMs = 30 * 60 * 1000L
            ) {
                prompt(routineId, blockId)
            }
        }
    }
}
