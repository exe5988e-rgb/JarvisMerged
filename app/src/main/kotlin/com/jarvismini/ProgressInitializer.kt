package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressEntry
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressState
import com.jarvismini.core.routine.RoutineProvider
import kotlinx.coroutines.runBlocking

object ProgressInitializer {
    fun registerAllBlocks(context: Context) = runBlocking {
        ProgressRepository.hydrate(context)
        val routines = RoutineProvider.getAllRoutines(context)
        routines.forEach { routine ->
            val entry = ProgressEntry(
                routineId = routine.id,
                blockId = routine.id,
                timestamp = System.currentTimeMillis(),
                state = ProgressState.PENDING,
                scheduledAt = routine.scheduledAt // <-- use actual property name from Routine
            )
            ProgressRepository.register(context, entry)
        }
    }
}
