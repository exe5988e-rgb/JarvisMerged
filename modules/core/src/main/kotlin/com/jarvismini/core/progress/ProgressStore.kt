package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Central persistent store for all ProgressEntry objects.
 *
 * Guarantees:
 * - Idempotent initialization
 * - Synchronous load (off-main-thread)
 * - No duplicates, no overwrites
 * - Full time-aware progress tracking
 */
object ProgressStore {

    private const val FILE_NAME = "progress_store.json"

    // In-memory cache of all progress entries
    private val entries: MutableMap<String, ProgressEntry> = mutableMapOf()

    // Guard to prevent multiple init loads
    @Volatile
    private var initialized = false

    /**
     * Initialize the ProgressStore by loading persisted entries.
     * Safe to call multiple times (idempotent). Must be called before engine usage.
     * Off-main-thread recommended for I/O.
     */
    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val json = file.readText()
                val list: List<ProgressEntry> = Json.decodeFromString(json)
                list.forEach { entry ->
                    // Merge without overwriting existing in-memory entries
                    val key = makeKey(entry.routineId, entry.blockId)
                    entries.putIfAbsent(key, entry)
                }
            } catch (_: Exception) {
                // Ignore corrupted file, start fresh
            }
        }
        initialized = true
    }

    /** Persist current entries to disk. */
    private suspend fun persist(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        try {
            val json = Json.encodeToString(entries.values.toList())
            file.writeText(json)
        } catch (_: Exception) {
            // Ignore write failures; best-effort persistence
        }
    }

    /** Unique key for internal map */
    private fun makeKey(routineId: String, blockId: String) = "$routineId::$blockId"

    /** Register a block if not already present */
    suspend fun register(context: Context, entry: ProgressEntry) {
        val key = makeKey(entry.routineId, entry.blockId)
        entries.putIfAbsent(key, entry)
        persist(context)
    }

    /** Mark a block as complete. Updates completedAt and lastUpdatedAt if not already set */
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

    /** Mark a block as incomplete. Updates state and lastUpdatedAt */
    suspend fun markIncomplete(context: Context, routineId: String, blockId: String) {
        val key = makeKey(routineId, blockId)
        val existing = entries[key]
        if (existing != null) {
            val updated = existing.copy(
                state = ProgressState.INCOMPLETE,
                lastUpdatedAt = System.currentTimeMillis()
            )
            entries[key] = updated
            persist(context)
        }
    }

    /** Retrieve all progress entries */
    fun getAllEntries(): List<ProgressEntry> = entries.values.toList()

    /** Retrieve all entries for today (optional filter by date can be added later) */
    fun getTodayEntries(): List<ProgressEntry> = entries.values.toList()
}
