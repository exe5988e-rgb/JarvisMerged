package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.routine.model.Routine

/**
 * Executes routines and reports completion to ProgressEngine.
 * Currently stubbed: routine actions are ignored until defined.
 */
class RoutineExecutor(
    private val context: Context
) {

    fun execute(routine: Routine) {
        // TODO: Implement action execution when Routine.actions exists
        // Currently skipping actions for compilation purposes

        // ✅ Mark routine as complete in ProgressEngine
        ProgressEngine.markComplete(context, routine.id)
    }
}
