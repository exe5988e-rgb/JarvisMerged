package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.routine.RoutineProvider

/**
 * Auto-register all routines as checklist blocks on app start.
 */
object ProgressInitializer {
    fun registerAllBlocks(context: Context) {
        val routines = RoutineProvider.getAllRoutines(context)
        routines.forEach { routine ->
            // register with scheduled time
            ProgressRepository.register(context, routine.id, routine.time)
        }
    }
}
