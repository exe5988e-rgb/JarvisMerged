package com.jarvismini.core.routine

import android.content.Context
import com.jarvismini.core.routine.model.Routine
import com.jarvismini.core.util.JsonUtil

/**
 * Loads all routines from assets/routines/*.json
 */
object RoutineProvider {

    fun getAllRoutines(context: Context): List<Routine> {
        val routineFiles = listOf(
            "progress_config.json",
            "sunday_routine.json",
            "work_routine.json"
        )

        val routines = mutableListOf<Routine>()
        routineFiles.forEach { fileName ->
            val jsonText = JsonUtil.loadJsonFromAssets(context, "routines/$fileName")
            jsonText?.let {
                val parsed = JsonUtil.fromJsonArray<Routine>(it)
                routines.addAll(parsed)
            }
        }
        return routines
    }
}
