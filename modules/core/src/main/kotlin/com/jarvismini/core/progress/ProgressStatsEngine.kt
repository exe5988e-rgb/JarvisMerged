package com.jarvismini.core.progress

import java.text.SimpleDateFormat
import java.util.*

object ProgressStatsEngine {

    fun getTodayStats(): ProgressStats {
        val blocks = ProgressStore.getTodayBlocks() // zero-arg
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
