package com.jarvismini.ui.checklist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.ui.ChecklistItem

@Composable
fun ChecklistScreen() {
    val context = LocalContext.current
    var blocks by remember { mutableStateOf<List<ProgressBlock>>(emptyList()) }

    LaunchedEffect(Unit) {
        blocks = ProgressRepository.getTodayBlocks(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(blocks) { block ->
            ChecklistItem(
                block = block,
                onComplete = {
                    ProgressRepository.markCompleted(context, block.id)
                    AssistantTTS.speak(
                        context,
                        "${block.name} marked as complete"
                    )

                    blocks = blocks.map {
                        if (it.id == block.id) it.copy(completed = true) else it
                    }
                },
                onIncomplete = {
                    ProgressRepository.markIncomplete(context, block.id, block.name)
                    AssistantTTS.speak(
                        context,
                        "Okay, I will remind you about ${block.name} later"
                    )
                }
            )
        }
    }
}
