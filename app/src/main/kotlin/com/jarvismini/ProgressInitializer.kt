package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressStore
import com.jarvismini.core.routine.RoutineProvider

/**
 * Auto-register all routines as checklist blocks on app start.
 */
object ProgressInitializer {
    fun registerAllBlocks(context: Context) {
        // Load all original routines from assets
        val routines = RoutineProvider.getAllRoutines(context)
        routines.forEach { routine ->
            // Register each routine ID as a block
            ProgressStore.register(context, routine.id)

            // Optional: mark as incomplete initially
            ProgressStore.markIncomplete(context, routine.id)
        }
    }
}
