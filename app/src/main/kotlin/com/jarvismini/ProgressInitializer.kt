package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.*
import com.jarvismini.core.routine.RoutineProvider
import kotlinx.coroutines.runBlocking

object ProgressInitializer {

    fun registerAllBlocks(context: Context) = runBlocking {
        ProgressStore.init(context)

        val today = System.currentTimeMillis()
        val existing = ProgressStore.getTodayEntries()
            .map { it.blockId }
            .toSet()

        RoutineProvider.getAllRoutines(context).forEach { routine ->
            if (routine.id !in existing) {
                ProgressStore.register(
                    context,
                    ProgressEntry(
                        routineId = routine.id,
                        blockId = routine.id,
                        timestamp = today
                    )
                )
            }
        }
    }
}
