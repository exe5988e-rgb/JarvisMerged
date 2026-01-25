package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.routine.model.Routine

/**
 * Executes a Routine by dispatching actions and notifying ProgressEngine.
 */
class RoutineExecutor(
    private val context: Context
) {
    fun execute(routine: Routine) {
        routine.actions.forEach { action ->
            ActionDispatcher.dispatch(context, action)
        }

        // ✅ BLOCK FINISHED → PROGRESS ENGINE
        ProgressEngine.markComplete(routine.id)
    }
}
