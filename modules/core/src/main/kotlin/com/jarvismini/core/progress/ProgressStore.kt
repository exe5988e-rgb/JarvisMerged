package com.jarvismini.core.progress

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ProgressStore {

    private const val FILE_NAME = "progress_store.json"
    private val gson = Gson()
    private val entries: MutableMap<String, ProgressEntry> = mutableMapOf()
    @Volatile private var initialized = false

    private fun makeKey(routineId: String, blockId: String) = "$routineId::$blockId"

    suspend fun init(context: Context) {
        if (initialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val type = object : TypeToken<List<ProgressEntry>>() {}.type
                val list: List<ProgressEntry> = gson.fromJson(file.readText(), type)
                list.forEach { entry ->
                    if (!entries.containsKey(makeKey(entry.routineId, entry.blockId))) {
                        entries[makeKey(entry.routineId, entry.blockId)] = entry
                    }
                }
            } catch (_: Exception) { }
        }
        initialized = true
    }

    private suspend fun persist(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        try {
            val json = gson.toJson(entries.values.toList())
            file.writeText(json)
        } catch (_: Exception) { }
    }

    suspend fun register(context: Context, entry: ProgressEntry) {
        val key = makeKey(entry.routineId, entry.blockId)
        entries.putIfAbsent(key, entry)
        persist(context)
    }

    suspend fun markComplete(context: Context, routineId: String, blockId: String) {
        val key = makeKey(routineId, blockId)
        val existing = entries[key]
        if (existing != null && existing.completedAt == null) {
            val updated = existing.copy(
                state = ProgressState.COMPLETED,
                completedAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
            entries[key] = updated
            persist(context)
        }
    }

    suspend fun markIncomplete(context: Context, routineId: String, blockId: String) {
        val key = makeKey(routineId, blockId)
        val existing = entries[key]
        if (existing != null) {
            val now = System.currentTimeMillis()
            val missedTime = if (existing.scheduledAt != null &&
                now > existing.scheduledAt &&
                existing.state != ProgressState.COMPLETED
            ) now else existing.missedAt
            val updated = existing.copy(
                state = ProgressState.INCOMPLETE,
                lastUpdatedAt = now,
                missedAt = missedTime
            )
            entries[key] = updated
            persist(context)
        }
    }

    fun getAllEntries(): List<ProgressEntry> = entries.values.toList()

    fun getTodayEntries(): List<ProgressEntry> {
        val today = System.currentTimeMillis()
        return entries.values.filter {
            it.scheduledAt != null && isSameDay(it.scheduledAt, today)
        }
    }

    fun getTodayBlocks(): List<ProgressBlock> =
        getTodayEntries().map { ProgressBlock(it.blockId, it.state == ProgressState.COMPLETED) }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    suspend fun updateMissedTasks(context: Context) {
        entries.values.forEach { entry ->
            if (entry.scheduledAt != null &&
                entry.state != ProgressState.COMPLETED &&
                System.currentTimeMillis() > entry.scheduledAt
            ) {
                markIncomplete(context, entry.routineId, entry.blockId)
            }
        }
    }
}
