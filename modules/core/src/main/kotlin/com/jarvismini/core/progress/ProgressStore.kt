package com.jarvismini.core.progress

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ProgressStore {

    private const val FILE_NAME = "progress_store.json"
    private val gson = Gson()

    private val entries: MutableMap<String, ProgressEntry> = mutableMapOf()
    @Volatile
    private var initialized = false

    private fun makeKey(routineId: String, blockId: String) = "$routineId::$blockId"

    suspend fun init(context: Context) {
        if (initialized) return
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val type = object : TypeToken<List<ProgressEntry>>() {}.type
                val list: List<ProgressEntry> = gson.fromJson(file.readText(), type)
                list.forEach { entry ->
                    entries.putIfAbsent(makeKey(entry.routineId, entry.blockId), entry)
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

    suspend fun markComplete(context: Context, blockId: String) {
        val key = makeKey(blockId, blockId)
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

    suspend fun markIncomplete(context: Context, blockId: String) {
        val key = makeKey(blockId, blockId)
        val existing = entries[key]
        if (existing != null) {
            val now = System.currentTimeMillis()
            val missedTime = if (existing.scheduledAt != null && now > existing.scheduledAt && existing.state != ProgressState.COMPLETED) now else existing.missedAt
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
    fun getTodayEntries(): List<ProgressEntry> = entries.values.toList()

    fun getRegisteredBlocks(): Set<String> = entries.keys.map { it.split("::")[1] }.toSet()
    fun getCompletedBlocks(): Set<String> = entries.values.filter { it.state == ProgressState.COMPLETED }.map { it.blockId }.toSet()
    fun getTodayBlocks(): List<ProgressBlock> =
        entries.values.map { ProgressBlock(it.blockId, it.state == ProgressState.COMPLETED) }
}
