package com.jarvismini.core.progress

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository layer for progress management.
 * Bridges engine / initializer calls to the persistent ProgressStore.
 *
 * Guarantees:
 * - Idempotent hydration
 * - Merge-safe updates
 * - Time-aware progress entries
 */
object ProgressRepository {

    private var hydrated = false

    /**
     * Hydrate repository with persisted entries.
     * Idempotent: safe to call multiple times.
     */
    suspend fun hydrate(context: Context) = withContext(Dispatchers.IO) {
        if (hydrated) return@withContext

        // Ensure ProgressStore is initialized
        ProgressStore.init(context)

        // Merge any entries from the store into repository memory
        val storeEntries = ProgressStore.getAllEntries()
        storeEntries.forEach { entry ->
            val key = makeKey(entry.routineId, entry.blockId)
            // merge safely: do not overwrite existing entries in the store
            ProgressStore.register(context, entry)
        }

        hydrated = true
    }

    /** Register a new block if it does not already exist */
    suspend fun register(context: Context, entry: ProgressEntry) {
        ProgressStore.register(context, entry)
    }

    /** Mark a block as completed */
    suspend fun markCompleted(context: Context, routineId: String, blockId: String) {
        ProgressStore.markComplete(context, routineId, blockId)
    }

    /** Mark a block as incomplete */
    suspend fun markIncomplete(context: Context, routineId: String, blockId: String) {
        ProgressStore.markIncomplete(context, routineId, blockId)
    }

    /** Retrieve all progress entries */
    fun getAllEntries(): List<ProgressEntry> = ProgressStore.getAllEntries()

    /** Retrieve today's entries */
    fun getTodayEntries(): List<ProgressEntry> = ProgressStore.getTodayEntries()

    /** Internal unique key for map-based operations */
    private fun makeKey(routineId: String, blockId: String) = "$routineId::$blockId"
}
