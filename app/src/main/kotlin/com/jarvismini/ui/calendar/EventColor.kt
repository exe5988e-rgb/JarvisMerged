package com.jarvismini.ui.calendar

import androidx.compose.ui.graphics.Color
import com.jarvismini.core.calendar.model.EventStatus

fun statusColor(status: EventStatus): Color = when (status) {
    EventStatus.SCHEDULED -> Color(0xFF64B5F6)      // Blue
    EventStatus.COMPLETED -> Color(0xFF81C784)      // Green
    EventStatus.INCOMPLETE -> Color(0xFFE57373)     // Red ✅ fixed
    EventStatus.RETRY_SCHEDULED -> Color(0xFFFFB74D)// Orange ✅ fixed
    else -> Color.Gray                               // ✅ exhaustive 'when'
}
