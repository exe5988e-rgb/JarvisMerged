package com.jarvismini.core.progress

import android.content.Context

object ProgressStore {

    private val completedBlocks = mutableSetOf<String>()

    fun markComplete(context: Context, blockId: String) {
        completedBlocks.add(blockId)
    }

    fun markIncomplete(context: Context, blockId: String) {
        completedBlocks.remove(blockId)
    }

    /**
     * Returns all blocks scheduled for today.
     * Minimal in-memory stub so stats + reminders compile.
     */
    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        return completedBlocks.map {
            ProgressBlock(
                id = it,
                completed = true
            )
        }
    }
}
