package com.jarvismini.core.progress

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Calendar

object ProgressStore {

    private const val FILE_NAME = "progress_store.json"
    private val gson = Gson()
    private val entries = mutableMapOf<String, ProgressEntry>()
    @Volatile private var initialized = false

    private fun key(routineId: String, blockId: String) = "$routineId::$blockId"

    suspend fun init(context: Context) {
        if (initialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            runCatching {
                val type = object : TypeToken<List<ProgressEntry>>() {}.type
                val list: List<ProgressEntry> = gson.fromJson(file.readText(), type)
                list.forEach { entries[key(it.routineId, it.blockId)] = it }
            }
        }
        initialized = true
    }

    private fun persist(context: Context) {
        runCatching {
            File(context.filesDir, FILE_NAME)
                .writeText(gson.toJson(entries.values.toList()))
        }
    }

    suspend fun register(context: Context, entry: ProgressEntry) {
        val k = key(entry.routineId, entry.blockId)
        if (!entries.containsKey(k)) {
            entries[k] = entry
            persist(context)
        }
    }

    suspend fun markComplete(context: Context, routineId: String, blockId: String) {
        val k = key(routineId, blockId)
        entries[k]?.let {
            entries[k] = it.copy(
                state = ProgressState.COMPLETED,
                completedAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
            persist(context)
        }
    }

    suspend fun markIncomplete(context: Context, routineId: String, blockId: String) {
        val k = key(routineId, blockId)
        entries[k]?.let {
            entries[k] = it.copy(
                state = ProgressState.INCOMPLETE,
                missedAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
            persist(context)
        }
    }

    fun updateMissedTasks(context: Context) {
        val now = System.currentTimeMillis()
        entries.values.forEach {
            if (it.state == ProgressState.PENDING && now > it.scheduledAt) {
                entries[key(it.routineId, it.blockId)] =
                    it.copy(state = ProgressState.INCOMPLETE, missedAt = now)
            }
        }
        persist(context)
    }

    fun getTodayEntries(): List<ProgressEntry> =
        entries.values.filter { isToday(it.scheduledAt) }

    fun getTodayBlocks(): List<ProgressBlock> =
        getTodayEntries().map {
            ProgressBlock(it.blockId, it.state == ProgressState.COMPLETED)
        }

    private fun isToday(time: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = time }
        val c2 = Calendar.getInstance()
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
