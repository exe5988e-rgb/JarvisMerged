package com.jarvismini.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.text.format.DateFormat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import com.jarvismini.ui.theme.GlassSurface
import com.jarvismini.ui.theme.JarvisBackground
import com.jarvismini.ui.theme.JarvisBlack
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import com.jarvismini.ui.theme.staggerEnter
import androidx.compose.ui.text.font.FontFamily
import com.jarvismini.core.calendar.model.CalendarEvent
import com.jarvismini.core.utils.TimeUtils
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.core.calendar.model.EventStatus
import java.util.*

/**
 * FULLY WORKING DayCalendarScreen with back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayCalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit) {
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

    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val dateLabel = remember(dayStart) {
        java.text.SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()).uppercase()
    }
    val nextEvent = events.firstOrNull { it.status == EventStatus.SCHEDULED }

    // Same HUD backdrop as Debug / Termux so the glass summary strip has something to sit on.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(JarvisBlack, JarvisBackground, JarvisBlack)))
    ) {
        // ===== TOP APP BAR WITH BACK BUTTON =====
        TopAppBar(
            title = {
                Text(
                    "Today's Schedule",
                    color = JarvisBlue,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier.semantics { heading() }
                )
            },
            navigationIcon = {
                // IconButton is 48dp by default.
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Navigate back", tint = JarvisBlue)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = JarvisBlack.copy(alpha = 0.6f)
            )
        )

        // Summary strip: one glass surface, outside the scrolling list.
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp)
                .staggerEnter(0)
                .semantics(mergeDescendants = true) {},
            shape = RoundedCornerShape(JarvisRadius.Card),
            cornerTicks = true
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = dateLabel,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JarvisTextSecondary
                )
                Text(
                    text = when {
                        events.isEmpty() -> "No routines today"
                        nextEvent != null ->
                            "${events.size} routines · next at ${timeFormat.format(Date(nextEvent.startTimeMs))}"
                        else -> "${events.size} routines · all done"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = JarvisTextPrimary
                )
            }
        }

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics(mergeDescendants = true) {}
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = JarvisTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "No routines scheduled for today",
                        style = MaterialTheme.typography.bodyLarge,
                        color = JarvisTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
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
