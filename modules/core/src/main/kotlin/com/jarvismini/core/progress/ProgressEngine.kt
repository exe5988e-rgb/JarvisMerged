package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.notifications.ProgressNotifier
import com.jarvismini.core.tts.AssistantTTS

object ProgressEngine {

    private lateinit var context: Context
    private lateinit var config: ProgressConfig
    private var notificationIconRes: Int = 0

    fun init(
        ctx: Context,
        cfg: ProgressConfig,
        iconRes: Int
    ) {
        context = ctx.applicationContext
        config = cfg
        notificationIconRes = iconRes
    }

    fun onBlockCompleted(blockId: String, blockName: String) {
        if (!config.enabled) return

        ProgressStatsEngine.registerBlock(context, blockId)

        ProgressNotifier.showCompletionPrompt(
            context,
            blockId,
            blockName,
            notificationIconRes
        )

        if (config.ttsEnabled) {
            AssistantTTS.speak(
                context,
                "Block $blockName finished. Please confirm completion."
            )
        }
    }

    fun markComplete(blockId: String) {
        ProgressStore.markComplete(context, blockId)
        ProgressStatsEngine.markCompleted(context, blockId)
    }

    fun markIncomplete(blockId: String, blockName: String) {
        ProgressStore.markIncomplete(context, blockId)
        if (config.retryEnabled) {
            RetryScheduler.schedule(context, blockId, blockName, config)
        
}
    }
}
