package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ProgressStatsEngine {

    // Existing method (kept intact)
    fun getTodayStats(): ProgressStats {
        val blocks = ProgressStore.getTodayBlocks()
        val completed = blocks.count { it.completed }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return ProgressStats(today, blocks.size, completed)
    }

    // 🔧 Added for ProgressEngine compatibility
    fun getStats(context: Context): StatsWrapper {
        val blocks = ProgressStore.getTodayBlocks()

        val completed = blocks.count { it.completed }
        val missed = blocks.count { it.missedAt != null }
        val pending = blocks.size - completed - missed
        val total = blocks.size.coerceAtLeast(1)

        val rate = (completed.toFloat() / total.toFloat()) * 100f

        return StatsWrapper(
            completedCount = completed,
            pendingCount = pending,
            missedCount = missed,
            completionRate = rate
        )
    }

    data class StatsWrapper(
        val completedCount: Int,
        val pendingCount: Int,
        val missedCount: Int,
        val completionRate: Float
    )
}
