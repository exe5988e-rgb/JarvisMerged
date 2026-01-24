package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressStore
import com.jarvismini.core.routine.RoutineLoader
import com.jarvismini.core.routine.model.Routine

/**
 * Auto-register all routines as checklist blocks on app start.
 */
object ProgressInitializer {

    fun registerAllBlocks(context: Context) {
        // Load all your original routines
        val routines: List<Routine> = RoutineLoader.loadRoutines(context)

        routines.forEach { routine ->
            // Register each routine ID as a block
            ProgressStore.register(context, routine.id)
        }
    }
}
