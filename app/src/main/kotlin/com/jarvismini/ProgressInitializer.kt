package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.routine.RoutineProvider

/**
 * Initializes all routines and registers them with ProgressRepository.
 */
object ProgressInitializer {
    fun registerAllBlocks(context: Context) {
        // Load routines from assets using RoutineProvider
        val routines = RoutineProvider.getAllRoutines(context)
        routines.forEach { routine ->
            // Ensure trigger time exists
            val scheduledTime = routine.trigger?.time ?: "00:00"
            ProgressRepository.register(context, routine.id, scheduledTime)
        }
    }
}
