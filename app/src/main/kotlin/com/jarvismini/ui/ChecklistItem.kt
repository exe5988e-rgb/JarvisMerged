package com.jarvismini.ui

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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = block.name,
                modifier = Modifier.weight(1f)
            )

            if (!block.completed) {
                TextButton(onClick = onComplete) { Text("Done") }
                TextButton(onClick = onIncomplete) { Text("Missed") }
            } else {
                Text("✅ Completed")
            }
        }
    }
}
