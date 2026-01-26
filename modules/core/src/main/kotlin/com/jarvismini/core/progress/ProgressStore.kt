package com.jarvismini.core.progress

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Calendar

object ProgressStore {

    private const val FILE_NAME = "progress_store.json"
    private val gson = Gson()
    private val entries = mutableListOf<ProgressEntry>()
    private var initialized = false

    suspend fun init(context: Context) {
        if (initialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            val type = object : TypeToken<List<ProgressEntry>>() {}.type
            entries.addAll(gson.fromJson(file.readText(), type))
        }
        initialized = true
    }

    private fun persist(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(gson.toJson(entries))
    }

    suspend fun register(context: Context, entry: ProgressEntry) {
        if (entries.none { it.routineId == entry.routineId && isSameDay(it.scheduledAt, entry.scheduledAt) }) {
            entries.add(entry)
            persist(context)
        }
    }

    fun getTodayEntries(): List<ProgressEntry> {
        val now = System.currentTimeMillis()
        return entries.filter { isSameDay(it.scheduledAt, now) }
    }

    fun getTodayBlocks(): List<ProgressBlock> =
        getTodayEntries().map {
            ProgressBlock(
                id = it.blockId,
                completed = it.state == ProgressState.COMPLETED
            )
        }

    suspend fun markComplete(context: Context, blockId: String) {
        val now = System.currentTimeMillis()
        entries.replaceAll {
            if (it.blockId == blockId && isSameDay(it.scheduledAt, now))
                it.copy(state = ProgressState.COMPLETED, completedAt = now)
            else it
        }
        persist(context)
    }

    suspend fun markMissed(context: Context, blockId: String) {
        val now = System.currentTimeMillis()
        entries.replaceAll {
            if (it.blockId == blockId && isSameDay(it.scheduledAt, now))
                it.copy(state = ProgressState.INCOMPLETE, missedAt = now)
            else it
        }
        persist(context)
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = a }
        val c2 = Calendar.getInstance().apply { timeInMillis = b }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
