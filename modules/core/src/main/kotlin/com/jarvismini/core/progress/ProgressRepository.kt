package com.jarvismini.core.progress

import android.content.Context

object ProgressRepository {

    fun register(context: Context, blockId: String, scheduledTime: String) {
        ProgressStore.register(context, blockId, scheduledTime)
    }

    fun markCompleted(context: Context, blockId: String) {
        ProgressStore.markComplete(context, blockId)
    }

    fun markMissed(context: Context, blockId: String) {
        ProgressStore.markMissed(context, blockId)
    }

    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        return ProgressStore.getTodayBlocks(context)
    }
}
