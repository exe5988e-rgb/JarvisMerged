package com.jarvismini.core.progress

import android.content.Context

/**
 * Repository for managing daily progress blocks.
 * Provides context-aware and context-free methods for UI and engine.
 */
object ProgressRepository {

    fun getTodayBlocks(context: Context? = null): List<ProgressBlock> {
        val registered = ProgressStore.getRegisteredBlocks(context ?: throw IllegalStateException("Context required"))
        val completed = ProgressStore.getCompletedBlocks(context ?: throw IllegalStateException("Context required"))

        return registered.map { blockId ->
            ProgressBlock(
                id = blockId,
                name = blockId,
                completed = completed.contains(blockId)
            )
        }
    }

    fun markCompleted(blockId: String) {
        ProgressEngine.markComplete(blockId)
    }

    fun markCompleted(context: Context, blockId: String) {
        ProgressEngine.markComplete(context, blockId)
    }

    fun markIncomplete(blockId: String, blockName: String) {
        ProgressEngine.markIncomplete(blockId, blockName)
    }

    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        ProgressEngine.markIncomplete(context, blockId, blockName)
    }
}
