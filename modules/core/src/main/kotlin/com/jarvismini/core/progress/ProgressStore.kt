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
    private var initialized = false

    private fun key(routineId: String, blockId: String) = "$routineId::$blockId"

    suspend fun init(context: Context) {
        if (initialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            val type = object : TypeToken<List<ProgressEntry>>() {}.type
            runCatching {
                gson.fromJson<List<ProgressEntry>>(file.readText(), type)
            }.getOrNull()?.forEach {
                entries[key(it.routineId, it.blockId)] = it
            }
        }
        initialized = true
    }

    private fun persist(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(gson.toJson(entries.values.toList()))
    }

    suspend fun register(context: Context, entry: ProgressEntry) {
        entries.putIfAbsent(key(entry.routineId, entry.blockId), entry)
        persist(context)
    }

    suspend fun markComplete(context: Context, blockId: String) {
        val entry = entries.values.find { it.blockId == blockId } ?: return
        entries[key(entry.routineId, entry.blockId)] =
            entry.copy(
                state = ProgressState.COMPLETED,
                completedAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
        persist(context)
    }

    suspend fun markIncomplete(context: Context, blockId: String) {
        val entry = entries.values.find { it.blockId == blockId } ?: return
        entries[key(entry.routineId, entry.blockId)] =
            entry.copy(
                state = ProgressState.INCOMPLETE,
                missedAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
        persist(context)
    }

    fun getTodayEntries(): List<ProgressEntry> {
        val now = System.currentTimeMillis()
        return entries.values.filter { isSameDay(it.timestamp, now) }
    }

    fun getTodayBlocks(): List<ProgressBlock> =
        getTodayEntries().map {
            ProgressBlock(it.blockId, it.state == ProgressState.COMPLETED)
        }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1[Calendar.YEAR] == c2[Calendar.YEAR] &&
               c1[Calendar.DAY_OF_YEAR] == c2[Calendar.DAY_OF_YEAR]
    }
}


---

✅ 4️⃣ ProgressStats (ONLY ONE DEFINITION)
