package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

data class ProgressStats(
    val date: String,
    val totalBlocks: Int,
    val completedBlocks: Int
) {
    val completionPercent: Int
        get() = if (totalBlocks == 0) 0 else (completedBlocks * 100) / totalBlocks
}

object ProgressStatsEngine {

    fun getTodayStats(context: Context): ProgressStats {
        val blocks = ProgressStore.getTodayBlocks() // pass context if needed
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
