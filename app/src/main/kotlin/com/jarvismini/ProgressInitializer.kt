package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressEntry
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressState
import kotlinx.coroutines.runBlocking

object ProgressInitializer {

    fun registerAllBlocks(context: Context) = runBlocking {
        ProgressRepository.hydrate(context)

        val today = System.currentTimeMillis()
        val todayRoutines = ProgressRepository.getTodayRoutines(context)

        todayRoutines.forEach { routine ->
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
