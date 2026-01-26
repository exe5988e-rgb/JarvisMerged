package com.jarvismini.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvismini.core.progress.*

@Composable
fun ChecklistScreen(blocks: List<ProgressBlock>, update: () -> Unit) {
    val context = LocalContext.current

    LazyColumn {
        items(blocks) { block ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(block.id)
                Row {
                    Button(onClick = {
                        ProgressEngine.markComplete(context, block.id)
                        update()
                    }) { Text("Done") }

                    Button(onClick = {
                        ProgressEngine.markIncomplete(context, block.id)
                        update()
                    }) { Text("Missed") }
                }
            }
        }
    }
}
