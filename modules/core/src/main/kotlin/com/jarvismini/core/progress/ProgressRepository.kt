package com.jarvismini.core.progress

import android.content.Context

object ProgressRepository {

    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        val completed = ProgressStore.getCompletedBlocks(context)
        return ProgressStore.getRegisteredBlocks(context).map { blockId ->
            ProgressBlock(
                id = blockId,
                name = blockId.replace("_", " ").uppercase(),
                completed = completed.contains(blockId)
            )
        }
    }

    fun markCompleted(context: Context, blockId: String) {
        ProgressEngine.markComplete(blockId)
    }

    fun markIncomplete(context: Context, blockId: String, name: String) {
        ProgressEngine.markIncomplete(blockId, name)
    }
}
