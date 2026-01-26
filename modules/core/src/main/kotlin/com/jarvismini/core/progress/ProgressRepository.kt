package com.jarvismini.core.progress

import android.content.Context
import com.jarvismini.core.routine.RoutineProvider
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object ProgressRepository {

    fun hydrate(context: Context) = runBlocking {
        ProgressStore.init(context)
        cleanupOldEntries(context)
    }

    fun register(context: Context, entry: ProgressEntry) =
        runBlocking { ProgressStore.register(context, entry) }

    fun markCompleted(context: Context, blockId: String) =
        runBlocking { ProgressStore.markComplete(context, blockId) }

    fun markIncomplete(context: Context, blockId: String) =
        runBlocking { ProgressStore.markIncomplete(context, blockId) }

    fun getTodayBlocks(): List<ProgressBlock> =
        ProgressStore.getTodayBlocks()

    fun getTodayRoutines(context: Context): List<com.jarvismini.core.routine.model.Routine> {
        val allRoutines = RoutineProvider.getAllRoutines(context)
        val today = getCurrentDayOfWeek()
        return allRoutines.filter { it.trigger?.days?.contains(today) == true }
    }

    private fun cleanupOldEntries(context: Context) {
        val todayStart = getTodayStartMs()
        val old = ProgressStore.getAllEntries().filter { it.scheduledAt < todayStart }
        old.forEach { ProgressStore.remove(context, it.blockId) }
    }

    private fun getCurrentDayOfWeek(): String {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "SUN"
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            else -> "SUN"
        }
    }

    private fun getTodayStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
