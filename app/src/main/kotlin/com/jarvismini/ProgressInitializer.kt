package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressEntry
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressState
import com.jarvismini.core.routine.RoutineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Responsible for initializing all progress-related data at app startup.
 *
 * Guarantees:
 * - ProgressStore is hydrated before engine or MissedTaskChecker run
 * - Initialization is synchronous from caller perspective but off main thread
 * - Idempotent: safe to call multiple times
 */
object ProgressInitializer {

    private val initScope = CoroutineScope(Dispatchers.Default)

    /**
     * Initialize progress system: load persisted progress, hydrate repository,
     * and register all routines as progress blocks.
     *
     * Call this at app bootstrap.
     */
    fun initialize(context: Context) {
        // Launch on a background thread but block until complete (synchronous for correctness)
        runBlocking {
            initScope.launch {
                // Step 1: Initialize the ProgressStore
                ProgressRepository.hydrate(context)

                // Step 2: Register all routines as progress blocks
                val routines = RoutineProvider.getAllRoutines(context)
                routines.forEach { routine ->
                    val entry = ProgressEntry(
                        routineId = routine.id,
                        blockId = routine.id, // use routine ID as block ID
                        timestamp = System.currentTimeMillis(),
                        state = ProgressState.PENDING,
                        scheduledAt = routine.scheduledAt ?: System.currentTimeMillis()
                    )
                    ProgressRepository.register(context, entry)
                }
            }.join() // Wait for initialization to complete before returning
        }
    }
}
