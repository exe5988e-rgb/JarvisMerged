package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.notifications.ProgressNotifier
import com.jarvismini.core.tts.AssistantTTS

object ProgressEngine {
    private lateinit var context: Context
    private lateinit var config: ProgressConfig

    fun init(ctx: Context, cfg: ProgressConfig) {
        context = ctx.applicationContext
        config = cfg
    }

    fun onBlockCompleted(blockId: String, blockName: String) {
        if (!config.enabled) return

        // 📊 Register block
        ProgressStatsEngine.registerBlock(context, blockId)
        ProgressNotifier.showCompletionPrompt(context, blockId, blockName)

        if (config.ttsEnabled) {
            AssistantTTS.speak(
                context,
                "Block ${blockName} finished. Please confirm completion."
            )
        }
    }

    fun markComplete(blockId: String) {
        ProgressStore.markComplete(context, blockId)
        ProgressStatsEngine.markCompleted(context, blockId)
    }

    fun markIncomplete(blockId: String, blockName: String) {
        ProgressStore.markIncomplete(context, blockId)
        scheduleRetry(blockId, blockName)
    }

    private fun scheduleRetry(blockId: String, blockName: String) {
        if (!config.retryEnabled) return
        RetryScheduler.schedule(context, blockId, blockName, config)
    }
}
