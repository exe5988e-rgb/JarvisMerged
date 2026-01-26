package com.jarvismini

import android.content.Context
import com.jarvismini.core.progress.*
import kotlinx.coroutines.runBlocking
import java.util.Calendar

object ProgressInitializer {

    fun registerAllBlocks(context: Context) = runBlocking {
        ProgressRepository.hydrate(context)
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
}
