package com.jarvismini.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Scheduled: ${formatTime(block.scheduledAt)}",
                style = MaterialTheme.typography.bodySmall
            )

            block.completedAt?.let {
                Text(
                    text = "Completed: ${formatTime(it)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onComplete,
                    enabled = !block.completed
                ) {
                    Text("Done")
                }

                OutlinedButton(
                    onClick = onIncomplete
                ) {
                    Text("Later")
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}
