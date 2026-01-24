package com.jarvismini.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.jarvismini.core.calendar.model.CalendarEvent
import com.jarvismini.core.utils.TimeUtils  // FIXED: correct package path

@Composable
fun DayCalendarScreen(viewModel: CalendarViewModel) {

    // FIXED: use the correct TimeUtils reference
    val now = TimeUtils.nowMs()
    val dayStart = now - (now % (24 * 60 * 60 * 1000))
    val dayEnd = dayStart + (24 * 60 * 60 * 1000)

    val events = remember {
        viewModel.getEventsForDay(dayStart, dayEnd)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        items(events) { event ->
            CalendarEventCard(event)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
