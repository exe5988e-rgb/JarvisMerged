package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.routine.RoutineProvider
import java.text.SimpleDateFormat
import java.util.*

object ProgressInitializer {
    fun registerAllBlocks(context: Context) {
        val routines = RoutineProvider.getAllRoutines(context)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        routines.forEach { routine ->
            val sched = dateFormat.format(routine.time) // routine.time assumed Date
            ProgressRepository.register(context, routine.id, sched)
        }
    }
}
