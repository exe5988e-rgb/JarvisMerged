package com.jarvismini.core.progress

import android.content.Context

object ProgressStatsEngine {

    fun registerBlock(context: Context, blockId: String) {
        ProgressStore.register(context, blockId)
    }

    fun markCompleted(context: Context, blockId: String) {
        ProgressStore.markComplete(context, blockId)
    }

    fun getTodayStats(context: Context): ProgressStats {
        val blocks = ProgressRepository.getTodayBlocks(context)
        val total = blocks.size
        val completedCount = blocks.count { it.completed }

        val completionPercent = if (total > 0) (completedCount * 100) / total else 0
        return ProgressStats(completionPercent = completionPercent)
    }
}

//===== Supporting data class =====
data class ProgressStats(
    val completionPercent: Int
)
