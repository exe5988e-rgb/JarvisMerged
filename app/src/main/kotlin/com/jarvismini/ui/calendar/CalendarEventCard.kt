package com.jarvismini.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvismini.core.calendar.model.CalendarEvent

@Composable
fun CalendarEventCard(event: CalendarEvent) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(statusColor(event.status))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Status: ${event.status}")
        }
    }
}
