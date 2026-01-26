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

        val today = System.currentTimeMillis()

        RoutineProvider.getAllRoutines(context)
            .filter { it.isScheduledForToday() }
            .forEach { routine ->
                ProgressRepository.register(
                    context,
                    ProgressEntry(
                        routineId = routine.id,
                        blockId = routine.id,
                        scheduledAt = today,
                        state = ProgressState.PENDING
                    )
                )
            }
    }
}
