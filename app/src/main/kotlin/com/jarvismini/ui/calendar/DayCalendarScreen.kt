package com.jarvismini.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.jarvismini.core.calendar.model.CalendarEvent
import com.jarvismini.core.utils.TimeUtils
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.core.calendar.model.EventStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * FIXED DayCalendarScreen - now shows routines as calendar events
 *
 * Changes:
 * - Fetches today's routines from RoutineProvider
 * - Converts routines to CalendarEvent objects
 * - Shows them in chronological order
 */
@Composable
fun DayCalendarScreen(viewModel: CalendarViewModel) {
    val context = LocalContext.current

    val now = TimeUtils.nowMs()
    val dayStart = now - (now % (24 * 60 * 60 * 1000))
    val dayEnd = dayStart + (24 * 60 * 60 * 1000)

    // Get today's routines and convert to calendar events
    val events = remember(dayStart) {
        val routines = RoutineProvider.getAllRoutines(context)
        val today = getCurrentDayOfWeek()

        routines
            .filter { routine ->
                routine.enabled && routine.trigger?.days?.contains(today) == true
            }
            .mapNotNull { routine ->
                routine.trigger?.time?.let { timeStr ->
                    val eventTime = parseTimeToMs(timeStr)
                    CalendarEvent(
                        id = routine.id,
                        title = routine.name,
                        startTimeMs = eventTime,
                        endTimeMs = eventTime + (60 * 60 * 1000), // 1 hour default duration
                        status = if (eventTime < now) EventStatus.COMPLETED else EventStatus.SCHEDULED,
                        meta = mapOf(
                            "description" to routine.actions.joinToString("\n") { action ->
                                when (action.type) {
                                    "speak" -> "🔊 ${action.params["message"]}"
                                    "notify" -> "🔔 ${action.params["message"]}"
                                    "set_mode" -> "🎯 Mode: ${action.params["mode"]}"
                                    "start_timer" -> "⏱️ Timer: ${action.params["task"]} (${action.params["duration"]} min)"
                                    else -> action.type
                                }
                            },
                            "calendarId" to "routines",
                            "type" to "routine"
                        )
                    )
                }
            }
            .sortedBy { it.startTimeMs }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Header
        Text(
            text = "Today's Schedule",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (events.isEmpty()) {
            // Show empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No routines scheduled for today",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Show events
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { event ->
                    CalendarEventCard(event)
                }
            }
        }
    }
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
