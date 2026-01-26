package com.jarvismini.core.progress

import android.content.Context
import java.util.Calendar

object ProgressStore {

    private val entries = mutableListOf<ProgressEntry>()

    fun init(context: Context) {
        // no-op for now
    }

    fun register(context: Context, entry: ProgressEntry) {
        if (entries.none { it.blockId == entry.blockId && isToday(it.scheduledAt) }) {
            entries.add(entry)
        }
    }

    fun markComplete(context: Context, blockId: String) {
        entries.find { it.blockId == blockId && isToday(it.scheduledAt) }?.apply {
            state = ProgressState.COMPLETED
        }
    }

    fun markIncomplete(context: Context, blockId: String) {
        entries.find { it.blockId == blockId && isToday(it.scheduledAt) }?.apply {
            state = ProgressState.INCOMPLETE
            missedAt = System.currentTimeMillis()
        }
    }

    fun getTodayEntries(): List<ProgressEntry> =
        entries.filter { isToday(it.scheduledAt) }

    fun getTodayBlocks(): List<ProgressBlock> =
        getTodayEntries().map {
            ProgressBlock(
                id = it.blockId,
                completed = it.state == ProgressState.COMPLETED
            )
        }

    private fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }
}
