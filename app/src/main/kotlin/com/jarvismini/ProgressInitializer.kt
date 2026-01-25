package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.routine.RoutineProvider

object ProgressInitializer {

    fun registerAllBlocks(context: Context) {
        val routines = RoutineProvider.getAllRoutines(context)

        routines.forEach { routine ->
            try {
                // Only register enabled routines with a trigger
                if (routine.enabled && routine.trigger.time.isNotBlank()) {
                    ProgressRepository.register(context, routine.id, routine.trigger.time)
                }
            } catch (e: Exception) {
                // Log and skip routines with missing or malformed triggers
                android.util.Log.w("ProgressInitializer", "Skipping routine ${routine.id}: ${e.message}")
            }
        }
    }
}
