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

    fun markComplete(context: Context, blockId: String) =
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

    fun resetTodayBlocks(context: Context) = runBlocking {
        val todayBlocks = getTodayBlocks()
        todayBlocks.forEach { block ->
            register(
                context,
                ProgressEntry(
                    routineId   = block.id,
                    blockId     = block.id,
                    scheduledAt = block.scheduledAt,
                    state       = ProgressState.PENDING
                )
            )
        }
        getTodayRoutines(context).forEach { routine ->
            val scheduledTime = routine.trigger?.time?.let { parseTimeToMs(it) }
                ?: System.currentTimeMillis()
            register(
                context,
                ProgressEntry(
                    routineId   = routine.id,
                    blockId     = routine.id,
                    scheduledAt = scheduledTime,
                    state       = ProgressState.PENDING
                )
            )
        }
    }

    /**
     * FIX: Times before 03:00 (00:xx, 01:xx, 02:xx) belong to the NEXT calendar
     * day relative to the session start (i.e. the night side of the 3 AM window).
     * We always compute the timestamp relative to sessionStart so it lands inside
     * the correct [sessionStart, sessionStart+24h) window.
     */
    internal fun parseTimeToMs(timeStr: String): Long {
        return try {
            val parts  = timeStr.split(":")
            val hour   = parts[0].toInt()
            val minute = parts[1].toInt()

            val sessionStart = ProgressStore.getSessionStart()
            val cal = Calendar.getInstance().apply { timeInMillis = sessionStart }

            // Session starts at 03:00. Times from 03:00–23:59 are the same calendar
            // day as sessionStart. Times from 00:00–02:59 are the NEXT calendar day.
            if (hour < 3) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE,      minute)
            cal.set(Calendar.SECOND,      0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun cleanupOldEntries(context: Context) {
        val sessionStart = ProgressStore.getSessionStart()
        val old = ProgressStore.getAllEntries().filter { it.scheduledAt < sessionStart }
        old.forEach { ProgressStore.remove(context, it.blockId) }
    }

    private fun getCurrentDayOfWeek(): String {
        // FIX: if it's currently before 03:00, "today's routines" are actually
        // yesterday's day-of-week (we're still in the same 3 AM session).
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < 3) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY    -> "SUN"
            Calendar.MONDAY    -> "MON"
            Calendar.TUESDAY   -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY  -> "THU"
            Calendar.FRIDAY    -> "FRI"
            Calendar.SATURDAY  -> "SAT"
            else               -> "SUN"
        }
    }
}
