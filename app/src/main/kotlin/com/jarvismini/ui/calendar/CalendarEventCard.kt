package com.jarvismini.ui.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvismini.core.calendar.model.CalendarEvent
import com.jarvismini.core.calendar.model.EventStatus
import com.jarvismini.ui.theme.JarvisBlue
import com.jarvismini.ui.theme.JarvisRadius
import com.jarvismini.ui.theme.JarvisTextPrimary
import com.jarvismini.ui.theme.JarvisTextSecondary
import java.util.Date

/**
 * One row of the day schedule. Dense list item, so it stays a flat tinted card (no glass).
 * Status is conveyed by icon + text label + stripe colour, never colour alone.
 * The whole card is one TalkBack node: "06:30 to 07:30, Morning briefing, Scheduled".
 */
@Composable
fun CalendarEventCard(event: CalendarEvent) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val start = timeFormat.format(Date(event.startTimeMs))
    val end = timeFormat.format(Date(event.endTimeMs))
    val accent = statusColor(event.status)
    val label = statusLabel(event.status)
    val description = event.meta["description"].orEmpty()
    val shape = RoundedCornerShape(JarvisRadius.Card)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JarvisBlue.copy(alpha = 0.06f), shape)
            .border(1.dp, JarvisBlue.copy(alpha = 0.25f), shape)
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {}
    ) {
        // Status stripe
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = "$start – $end",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = JarvisTextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = JarvisTextPrimary
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = statusIcon(event.status),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent
                )
            }
        }
    }
}

private fun statusLabel(status: EventStatus): String = when (status) {
    EventStatus.SCHEDULED -> "Scheduled"
    EventStatus.COMPLETED -> "Completed"
    EventStatus.INCOMPLETE -> "Incomplete"
    EventStatus.RETRY_SCHEDULED -> "Retry scheduled"
}

private fun statusIcon(status: EventStatus): ImageVector = when (status) {
    EventStatus.SCHEDULED -> Icons.Default.DateRange
    EventStatus.COMPLETED -> Icons.Default.CheckCircle
    EventStatus.INCOMPLETE -> Icons.Default.Warning
    EventStatus.RETRY_SCHEDULED -> Icons.Default.Refresh
}
