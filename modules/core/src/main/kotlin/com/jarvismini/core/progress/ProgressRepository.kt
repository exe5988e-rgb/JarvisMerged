package com.jarvismini.core.progress

import android.content.Context

/**
 * Repository for managing daily progress blocks.
 * Provides context-aware and context-free methods for UI and engine.
 */
object ProgressRepository {

    fun getTodayBlocks(context: Context? = null): List<ProgressBlock> {
        // ✅ Get all registered blocks for today
        val registered = ProgressStore.getRegisteredBlocks(context)
        val completed = ProgressStore.getCompletedBlocks(context)

        return registered.map { blockId ->
            ProgressBlock(
                id = blockId,
                name = blockId, // Optionally map to a proper display name
                completed = completed.contains(blockId)
            )
        }
    }

    fun markCompleted(blockId: String) {
        ProgressEngine.markComplete(blockId)
    }

    fun markIncomplete(blockId: String, blockName: String) {
        ProgressEngine.markIncomplete(blockId, blockName)
    }
}
