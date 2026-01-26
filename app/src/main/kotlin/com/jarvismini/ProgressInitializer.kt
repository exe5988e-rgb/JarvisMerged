package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

object ProgressInitializer {

    fun registerAllBlocks(context: Context) {
        // Hydrate repository and cleanup old entries
        ProgressRepository.hydrate(context)
        val todayRoutines = ProgressRepository.getTodayRoutines(context)

        todayRoutines.forEach { routine ->
            // Use routine trigger time for scheduledAt
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

        // Schedule daily reset at 03:00 AM
        scheduleDailyReset(context)
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

    private fun scheduleDailyReset(context: Context) {
        GlobalScope.launch {
            while (true) {
                val now = Calendar.getInstance()
                val resetTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 3)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
                }

                val delayMs = resetTime.timeInMillis - now.timeInMillis
                delay(delayMs)
                ProgressRepository.resetTodayBlocks(context)
            }
        }
    }
}


---
