package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * Provides statistics about today’s progress.
 */
object ProgressStatsEngine {

    fun getTodayStats(context: Context): ProgressStats {
        val blocks = ProgressStore.getTodayBlocks(context) // pass context
        val total = blocks.size
        val completed = blocks.count { it.completed }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return ProgressStats(
            date = today,
            totalBlocks = total,
            completedBlocks = completed
        )
    }
}
