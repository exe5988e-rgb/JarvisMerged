package com.jarvismini.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressStore

@Composable
fun ChecklistScreen() {
    val context = LocalContext.current

    // Pull blocks from registered routines
    var blocks by remember { mutableStateOf(loadRoutineBlocks(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Today's Checklist",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn {
            items(blocks) { block ->
                ChecklistItem(
                    block = block,
                    onComplete = {
                        ProgressRepository.markCompleted(context, block.id)
                        blocks = loadRoutineBlocks(context)
                    },
                    onIncomplete = {
                        ProgressRepository.markIncomplete(context, block.id)
                        blocks = loadRoutineBlocks(context)
                    }
                )
            }
        }
    }
}

// Helper to load blocks from registered routines
private fun loadRoutineBlocks(context: android.content.Context): List<ProgressBlock> {
    val completed = ProgressStore.getCompletedBlocks(context)
    val registered = ProgressStore.getRegisteredBlocks(context)

    return registered.map { blockId ->
        ProgressBlock(
            id = blockId,
            name = blockId.replace("_", " ").uppercase(),
            completed = completed.contains(blockId)
        )
    }
}
