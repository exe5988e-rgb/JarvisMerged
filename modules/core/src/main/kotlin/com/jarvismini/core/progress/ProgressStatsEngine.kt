package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

// ✅ Single source of truth for stats
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

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // ✅ Correctly construct ProgressStats with all required parameters
        return ProgressStats(
            date = today,
            totalBlocks = total,
            completedBlocks = completedCount
        )
    }
}
