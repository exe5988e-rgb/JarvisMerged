package com.jarvismini.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressStatsEngine

@Composable
fun DailyChecklistScreen() {
    val context = LocalContext.current

    // ✅ State for blocks and stats
    var blocks by remember { mutableStateOf(ProgressRepository.getTodayBlocks(context)) }
    var stats by remember { mutableStateOf(ProgressStatsEngine.getTodayStats(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Today's Checklist (${stats.completionPercent}%)",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn {
            items(blocks) { block ->
                ChecklistItem(
                    block = block,
                    onComplete = {
                        ProgressRepository.markCompleted(context, block.id)
                        blocks = ProgressRepository.getTodayBlocks(context)
                        stats = ProgressStatsEngine.getTodayStats(context) // ✅ update stats
                    },
                    onIncomplete = {
                        ProgressRepository.markIncomplete(context, block.id, block.name)
                        blocks = ProgressRepository.getTodayBlocks(context)
                        stats = ProgressStatsEngine.getTodayStats(context) // ✅ update stats
                    }
                )
            }
        }
    }
}
