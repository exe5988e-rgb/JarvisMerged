package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressStore
import com.jarvismini.core.routine.RoutineProvider

/**
 * Auto-register all routines as checklist blocks on app start.
 * Ensures no duplicate registration.
 */
object ProgressInitializer {

    fun registerAllBlocks(context: Context) {
        val existingBlocks = ProgressStore.getRegisteredBlocks(context)
        val routines = RoutineProvider.getAllRoutines(context)

        routines.forEach { routine ->
            // Register only if not already registered
            if (!existingBlocks.contains(routine.id)) {
                ProgressStore.register(context, routine.id)
            }
        }
    }
}
