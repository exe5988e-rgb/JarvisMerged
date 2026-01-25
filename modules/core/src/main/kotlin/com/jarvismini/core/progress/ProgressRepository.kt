package com.jarvismini.core.progress

import android.content.Context

object ProgressRepository {

    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        val registered = ProgressStore.getRegisteredBlocks(context)
        val completed = ProgressStore.getCompletedBlocks(context)

        return registered.map { blockId ->
            ProgressBlock(
                id = blockId,
                name = blockId, // Optionally map to display name
                completed = completed.contains(blockId)
            )
        }
    }

    fun markCompleted(context: Context, blockId: String) {
        ProgressEngine.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        ProgressEngine.markIncomplete(context, blockId, blockName)
  
}
}
