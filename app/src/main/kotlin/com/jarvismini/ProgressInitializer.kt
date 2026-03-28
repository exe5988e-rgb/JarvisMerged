package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.*
import com.jarvismini.core.routine.RoutineProvider
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object ProgressInitializer {

    fun registerAllBlocks(context: Context) = runBlocking {
        ProgressRepository.hydrate(context)

        // The "day" resets at 3 AM. If now >= sessionStart, we're in a fresh session.
        // cleanupOldEntries inside hydrate() already removed stale entries, so we
        // just need to register today's routines (register() is idempotent — it skips
        // blockIds that already exist in the current session).
        val todayRoutines = ProgressRepository.getTodayRoutines(context)
        todayRoutines.forEach { routine ->
            val scheduledTime = routine.trigger?.time
                ?.let { ProgressRepository.parseTimeToMs(it) }
                ?: System.currentTimeMillis()
            ProgressRepository.register(
                context,
                ProgressEntry(
                    routineId   = routine.id,
                    blockId     = routine.id,
                    scheduledAt = scheduledTime,
                    state       = ProgressState.PENDING
                )
            )
        }
    }
}
