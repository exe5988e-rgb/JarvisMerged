package com.jarvismini.core.progress

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object ProgressStore {

    private const val PREFS      = "jarvis_progress"
    private const val KEY_ENTRIES = "entries"

    private val entries = mutableListOf<ProgressEntry>()

    fun init(context: Context) {
        if (entries.isNotEmpty()) return
        load(context)
    }

    fun register(context: Context, entry: ProgressEntry) {
        if (entries.none { it.blockId == entry.blockId && isCurrentSession(it.scheduledAt) }) {
            entries.add(entry)
            save(context)
        }
    }

    fun markComplete(context: Context, blockId: String) {
        update(context, blockId) {
            it.copy(state = ProgressState.COMPLETED, completedAt = System.currentTimeMillis())
        }
    }

    fun markIncomplete(context: Context, blockId: String) {
        update(context, blockId) {
            it.copy(state = ProgressState.INCOMPLETE, missedAt = System.currentTimeMillis())
        }
    }

    fun remove(context: Context, blockId: String) {
        entries.removeAll { it.blockId == blockId }
        save(context)
    }

    fun getAllEntries(): List<ProgressEntry> = entries.toList()

    fun getTodayEntries(): List<ProgressEntry> =
        entries.filter { isCurrentSession(it.scheduledAt) }

    fun getTodayBlocks(): List<ProgressBlock> =
        getTodayEntries().map {
            ProgressBlock(
                id          = it.blockId,
                completed   = it.state == ProgressState.COMPLETED,
                scheduledAt = it.scheduledAt,
                completedAt = it.completedAt,
                missedAt    = it.missedAt
            )
        }

    // ── Internal ────────────────────────────────────────────────────────

    private fun update(
        context: Context,
        blockId: String,
        transform: (ProgressEntry) -> ProgressEntry
    ) {
        // FIX: use isCurrentSession (3 AM boundary) instead of isToday (midnight boundary)
        val index = entries.indexOfFirst {
            it.blockId == blockId && isCurrentSession(it.scheduledAt)
        }
        if (index != -1) {
            entries[index] = transform(entries[index])
            save(context)
        }
    }

    private fun save(context: Context) {
        val array = JSONArray()
        entries.forEach {
            array.put(JSONObject().apply {
                put("routineId",   it.routineId)
                put("blockId",     it.blockId)
                put("scheduledAt", it.scheduledAt)
                put("state",       it.state.name)
                put("completedAt", it.completedAt ?: 0L)
                put("missedAt",    it.missedAt    ?: 0L)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }

    private fun load(context: Context) {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null) ?: return

        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            entries += ProgressEntry(
                routineId   = o.getString("routineId"),
                blockId     = o.getString("blockId"),
                scheduledAt = o.getLong("scheduledAt"),
                state       = ProgressState.valueOf(o.getString("state")),
                completedAt = o.optLong("completedAt").takeIf { it != 0L },
                missedAt    = o.optLong("missedAt").takeIf    { it != 0L }
            )
        }
    }

    /**
     * FIX: "today" = current 3 AM session window (3 AM yesterday → 3 AM today).
     *
     * The day resets at 03:00, NOT at midnight. So tasks at 00:20, 00:50, 01:00
     * belong to the SAME session as 19:00, 21:00, 23:30 the previous calendar night.
     *
     * Session window = [ sessionStart, sessionStart + 24h )
     * where sessionStart = most recent 03:00 that has already passed.
     */
    private fun isCurrentSession(timestamp: Long): Boolean {
        val sessionStart = getSessionStart()
        val sessionEnd   = sessionStart + 24 * 60 * 60 * 1000L
        return timestamp in sessionStart until sessionEnd
    }

    /**
     * Returns the epoch-ms of the most recent 03:00:00 that has already passed.
     * e.g. if now is Wed 01:30, this returns Tue 03:00 (same "day" session).
     * e.g. if now is Wed 09:00, this returns Wed 03:00.
     */
    internal fun getSessionStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 3)
        cal.set(Calendar.MINUTE,      0)
        cal.set(Calendar.SECOND,      0)
        cal.set(Calendar.MILLISECOND, 0)
        // If we haven't reached 03:00 yet today, go back to yesterday's 03:00
        if (System.currentTimeMillis() < cal.timeInMillis) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }
}
