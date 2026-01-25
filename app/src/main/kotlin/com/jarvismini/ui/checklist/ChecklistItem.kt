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
    onIncomplete: () -> Unit
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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Scheduled: ${block.scheduledTime}",
                style = MaterialTheme.typography.bodySmall
            )

            block.completedTime?.let {
                Text(
                    text = "Completed/Missed: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!block.completed) {
                    TextButton(onClick = onComplete) { Text("Done") }
                    TextButton(onClick = onIncomplete) { Text("Missed") }
                } else {
                    Text("✅ Completed")
                }
            }
        }
    }
}
