package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.ProgressEntry
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressState
import com.jarvismini.core.routine.RoutineProvider
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object ProgressInitializer {

    fun registerAllBlocks(context: Context) = runBlocking {
        ProgressRepository.hydrate(context)

        val todayMillis = System.currentTimeMillis()

        RoutineProvider.getAllRoutines(context)
            .filter { routine -> isToday(routine.scheduledAt, todayMillis) }
            .forEach { routine ->
                ProgressRepository.register(
                    context,
                    ProgressEntry(
                        routineId = routine.id,
                        blockId = routine.id,
                        scheduledAt = routine.scheduledAt,
                        state = ProgressState.PENDING
                    )
                )
            }
    }

    private fun isToday(timestamp: Long, today: Long = System.currentTimeMillis()): Boolean {
        val calNow = Calendar.getInstance().apply { timeInMillis = today }
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return calNow.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
               calNow.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }
}
