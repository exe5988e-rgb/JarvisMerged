package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.routine.model.Routine

/**
 * Executes routines - DO NOT auto-complete.
 * Completion is user-driven via UI.
 */
class RoutineExecutor(
    private val context: Context
) {

    fun execute(routine: Routine) {
        routine.actions.forEach { action ->
            ActionDispatcher.dispatch(context, action)
        }
        
        // ❌ DO NOT mark complete here
        // Completion happens via user interaction in ChecklistScreen
    }
}
