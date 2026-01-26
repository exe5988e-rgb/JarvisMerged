package com.jarvismini.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.routine.model.Routine
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChecklistItem(
    block: ProgressBlock,
    routine: Routine,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit
) {
    val displayName = routine.label
        .replace("_", " ")
        .replaceFirstChar { c: Char -> c.uppercaseChar() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                block.completed -> MaterialTheme.colorScheme.primaryContainer
                block.missedAt != null -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            // Routine name
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tasks (RoutineAction list)
            Column(modifier = Modifier.padding(start = 4.dp)) {
                routine.actions.forEach { action ->
                    Text(
                        text = "• ${action.label}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scheduled time
            Text(
                text = "Scheduled: ${formatTime(block.scheduledAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Completed or Missed time
            block.completedAt?.let {
                if (block.completed) {
                    Text(
                        text = "Completed: ${formatTime(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            block.missedAt?.let {
                Text(
                    text = "Missed: ${formatTime(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!block.completed) {
                    TextButton(onClick = onComplete) {
                        Text("Done")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onIncomplete) {
                        Text("Missed")
                    }
                } else {
                    Text(
                        text = "✅ Completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun formatTime(timestampMs: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
