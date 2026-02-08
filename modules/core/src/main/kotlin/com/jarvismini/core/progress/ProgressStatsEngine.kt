package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ProgressStatsEngine {

    fun getTodayStats(): ProgressStats {
        val blocks = ProgressStore.getTodayBlocks()
        val completed = blocks.count { it.completed }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return ProgressStats(today, blocks.size, completed)
    }

    fun getStats(context: Context): StatsWrapper {
        val blocks = ProgressStore.getTodayBlocks()

        val completed = blocks.count { it.completed }
        val missed = blocks.count { it.missedAt != null }
        val pending = blocks.size - completed - missed
        val total = blocks.size.coerceAtLeast(1)

        val rate = (completed.toFloat() / total.toFloat()) * 100f

        // total work time removed (TaskTimerStore deleted)
        val totalWorkTime: Long = 0L

        return StatsWrapper(
            completedCount = completed,
            pendingCount = pending,
            missedCount = missed,
            completionRate = rate,
            totalWorkTimeMs = totalWorkTime
        )
    }

    data class StatsWrapper(
        val completedCount: Int,
        val pendingCount: Int,
        val missedCount: Int,
        val completionRate: Float,
        val totalWorkTimeMs: Long
    )
}
