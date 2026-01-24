package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.progress.ProgressEngine
import com.jarvismini.core.routine.model.Routine

class RoutineExecutor(
    private val context: Context
) {

    fun execute(routine: Routine) {
        routine.actions.forEach { action ->
            ActionDispatcher.dispatch(context, action)
        }

        // ✅ BLOCK FINISHED → PROGRESS ENGINE
        ProgressEngine.onBlockCompleted(
            blockId = routine.id,
            blockName = routine.name
        )
    }
}
