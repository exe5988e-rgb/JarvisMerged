package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.routine.RoutineProvider

object ProgressInitializer {
    fun registerAllBlocks(context: Context) {
        val routines = RoutineProvider.getAllRoutines(context)
        routines.forEach { routine ->
            ProgressRepository.register(context, routine.id, routine.trigger.time)
        }
    }
}
