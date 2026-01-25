package com.jarvismini.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvismini.core.progress.ProgressBlock

@Composable
fun ChecklistItem(
    block: ProgressBlock,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit,
    scheduledTime: String,
    actualTime: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = block.id.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Scheduled: $scheduledTime | Completed/Missed: $actualTime",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (block.status == Status.PENDING) {
                    TextButton(onClick = onComplete) { Text("Done") }
                    TextButton(onClick = onIncomplete) { Text("Missed") }
                } else {
                    Text(
                        text = if (block.status == Status.COMPLETED) "✅ Completed" else "⚠ Missed"
                    )
                }
            }
        }
    }
}
