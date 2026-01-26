package com.jarvismini.core.progress

import android.content.Context
import java.util.Calendar

object ProgressStore {

    private val entries = mutableListOf<ProgressEntry>()

    fun init(context: Context) {
        // no-op
    }

    fun register(context: Context, entry: ProgressEntry) {
        if (entries.none { it.blockId == entry.blockId && isToday(it.scheduledAt) }) {
            entries.add(entry)
        }
    }

    fun markComplete(context: Context, blockId: String) {
        val entry = entries.find { it.blockId == blockId && isToday(it.scheduledAt) } ?: return
        entry.state = ProgressState.COMPLETED
    }

    fun markIncomplete(context: Context, blockId: String) {
        val entry = entries.find { it.blockId == blockId && isToday(it.scheduledAt) } ?: return
        entry.state = ProgressState.INCOMPLETE
        entry.missedAt = System.currentTimeMillis()
    }

    fun getAllEntries(): List<ProgressEntry> = entries.toList()

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
