package com.jarvismini.core.progress

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object ProgressStore {

    private const val PREFS = "jarvis_progress"
    private const val KEY_ENTRIES = "entries"

    private val entries = mutableListOf<ProgressEntry>()

    fun init(context: Context) {
        if (entries.isNotEmpty()) return
        load(context)
    }

    fun register(context: Context, entry: ProgressEntry) {
        if (entries.none { it.blockId == entry.blockId && isToday(it.scheduledAt) }) {
            entries.add(entry)
            save(context)
        }
    }

    fun markComplete(context: Context, blockId: String) {
        update(context, blockId) {
            it.copy(
                state = ProgressState.COMPLETED,
                completedAt = System.currentTimeMillis()
            )
        }
    }

    fun markIncomplete(context: Context, blockId: String) {
        update(context, blockId) {
            it.copy(
                state = ProgressState.INCOMPLETE,
                missedAt = System.currentTimeMillis()
            )
        }
    }

    fun remove(context: Context, blockId: String) {
        entries.removeAll { it.blockId == blockId }
        save(context)
    }

    fun getAllEntries(): List<ProgressEntry> =
        entries.toList()

    fun getTodayEntries(): List<ProgressEntry> =
        entries.filter { isToday(it.scheduledAt) }

    fun getTodayBlocks(): List<ProgressBlock> =
        getTodayEntries().map {
            ProgressBlock(
                id = it.blockId,
                completed = it.state == ProgressState.COMPLETED,
                scheduledAt = it.scheduledAt,
                completedAt = it.completedAt,
                missedAt = it.missedAt
            )
        }

    // ---------------- INTERNAL ----------------

    private fun update(
        context: Context,
        blockId: String,
        transform: (ProgressEntry) -> ProgressEntry
    ) {
        val index = entries.indexOfFirst {
            it.blockId == blockId && isToday(it.scheduledAt)
        }
        if (index != -1) {
            entries[index] = transform(entries[index])
            save(context)
        }
    }

    private fun save(context: Context) {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject().apply {
                    put("routineId", it.routineId)
                    put("blockId", it.blockId)
                    put("scheduledAt", it.scheduledAt)
                    put("state", it.state.name)
                    put("completedAt", it.completedAt ?: 0L)
                    put("missedAt", it.missedAt ?: 0L)
                }
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }

    private fun load(context: Context) {
        val json = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return

        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            entries += ProgressEntry(
                routineId = o.getString("routineId"),
                blockId = o.getString("blockId"),
                scheduledAt = o.getLong("scheduledAt"),
                state = ProgressState.valueOf(o.getString("state")),
                completedAt = o.optLong("completedAt").takeIf { it != 0L },
                missedAt = o.optLong("missedAt").takeIf { it != 0L }
            )
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }
}
