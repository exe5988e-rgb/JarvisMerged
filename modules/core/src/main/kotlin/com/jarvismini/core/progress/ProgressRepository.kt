package com.jarvismini.core.progress

import android.content.Context

/**
 * Repository facade for progress-related queries.
 * Keeps callers stable while storage evolves.
 */
object ProgressRepository {

    fun getTodayBlocks(context: Context): List<ProgressBlock> {
        return ProgressStore.getTodayBlocks(context)
    }

    fun markCompleted(context: Context, blockId: String) {
        ProgressStore.markComplete(context, blockId)
    }

    fun markIncomplete(context: Context, blockId: String, blockName: String) {
        ProgressStore.markIncomplete(context, blockId)
    }
}
