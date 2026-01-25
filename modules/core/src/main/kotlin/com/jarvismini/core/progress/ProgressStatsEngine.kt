package com.jarvismini.core.progress

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ProgressStatsEngine {

    fun getTodayStats(context: Context): ProgressStats {
        val blocks = ProgressStore.getTodayBlocks(context)
        val total = blocks.size
        val completedCount = blocks.count { it.completed }

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
