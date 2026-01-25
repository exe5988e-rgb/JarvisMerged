package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ProgressStatsEngine {

    fun registerBlock(context: Context, blockId: String) {
        ProgressStore.register(context, blockId)
    }

    fun markCompleted(context: Context, blockId: String) {
        ProgressStore.markComplete(context, blockId)
    }

    fun getTodayStats(context: Context): ProgressStats {
        // ✅ Fetch all registered blocks
        val registered = ProgressStore.getRegisteredBlocks(context)
        val completed = ProgressStore.getCompletedBlocks(context)

        // Auto-register any block that appears in ProgressEngine but not in store
        val todayBlocks = ProgressRepository.getTodayBlocks()
        todayBlocks.forEach { block ->
            if (!registered.contains(block.id)) {
                registerBlock(context, block.id)
            }
        }

        val total = todayBlocks.size
        val completedCount = todayBlocks.count { it.completed }

        val today = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())

        return ProgressStats(
            date = today,
            totalBlocks = total,
            completedBlocks = completedCount
        )
    }
}
