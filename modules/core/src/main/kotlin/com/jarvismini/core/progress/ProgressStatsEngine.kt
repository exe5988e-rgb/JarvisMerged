package com.jarvismini.core.progress

import android.content.Context
import java.time.LocalDate

object ProgressStatsEngine {

    private const val PREF = "progress_stats"

    fun registerBlock(context: Context, blockId: String) {
        val key = todayKey()
        val prefs = prefs(context)

        val total = prefs.getInt("${key}_total", 0) + 1
        prefs.edit().putInt("${key}_total", total).apply()
    }

    fun markCompleted(context: Context, blockId: String) {
        val key = todayKey()
        val prefs = prefs(context)

        val completed = prefs.getInt("${key}_completed", 0) + 1
        prefs.edit().putInt("${key}_completed", completed).apply()
    }

    fun getTodayStats(context: Context): ProgressStats {
        val key = todayKey()
        val prefs = prefs(context)

        return ProgressStats(
            date = key,
            totalBlocks = prefs.getInt("${key}_total", 0),
            completedBlocks = prefs.getInt("${key}_completed", 0)
        )
    }

    private fun todayKey(): String =
        LocalDate.now().toString()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
