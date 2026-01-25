package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.routine.model.Routine

/**
 * Executes routines and reports completion to ProgressEngine.
 */
class RoutineExecutor(
    private val context: Context
) {

    fun execute(routine: Routine) {
        // Execute all actions in the routine
        routine.actions.forEach { action ->
            ActionDispatcher.dispatch(context, action)
        }

        // Mark the routine complete
        ProgressEngine.markComplete(context, routine.id)
    }
}
