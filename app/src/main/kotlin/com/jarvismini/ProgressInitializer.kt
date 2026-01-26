package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.*
import com.jarvismini.core.routine.RoutineProvider
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object ProgressInitializer {

    /**
     * Registers all today's routines as ProgressBlocks.
     * Safe for daily use and after 3 AM reset.
     */
    fun registerAllBlocks(context: Context) = runBlocking {
        // Ensure the store is initialized and old entries cleaned
        ProgressRepository.hydrate(context)

        // Reset today's blocks if current time is after 3 AM but before next reset
        val now = System.currentTimeMillis()
        val todayReset = getTodayStartMs()
        if (now >= todayReset) {
            ProgressRepository.resetTodayBlocks(context)
        }

        // Load today's routines and register them
        val todayRoutines = ProgressRepository.getTodayRoutines(context)
        todayRoutines.forEach { routine ->
            val scheduledTime = routine.trigger?.time?.let { parseTimeToMs(it) } ?: System.currentTimeMillis()
            ProgressRepository.register(
                context,
                ProgressEntry(
                    routineId = routine.id,
                    blockId = routine.id,
                    scheduledAt = scheduledTime,
                    state = ProgressState.PENDING
                )
            )
        }
    }

    private fun parseTimeToMs(timeStr: String): Long {
        return try {
            val cal = Calendar.getInstance()
            val parts = timeStr.split(":")
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            cal.set(Calendar.MINUTE, parts[1].toInt())
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun getTodayStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 3) // 3 AM reset
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
