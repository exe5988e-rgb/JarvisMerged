package com.jarvismini.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvismini.ProgressInitializer
import com.jarvismini.core.progress.*
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.checklist.ChecklistItem

enum class MainTab { Chat, Checklist }

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(MainTab.Chat) }
    var blocks by remember { mutableStateOf<List<ProgressBlock>>(emptyList()) }

    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
    }

    LaunchedEffect(tab) {
        if (tab == MainTab.Checklist) {
            blocks = ProgressRepository.getTodayBlocks()
            val stats = ProgressStatsEngine.getTodayStats()
            AssistantTTS.speak(
                context,
                "You have ${stats.completedBlocks} of ${stats.totalBlocks} tasks completed today."
            )
        }
    }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = tab.ordinal) {
                MainTab.values().forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.name) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            when (tab) {
                MainTab.Chat -> JarvisChatScreen()
                MainTab.Checklist -> ChecklistScreen(blocks) { blocks = it }
            }
        }
    }
}

@Composable
private fun ChecklistScreen(
    blocks: List<ProgressBlock>,
    update: (List<ProgressBlock>) -> Unit
) {
    val context = LocalContext.current
    val stats = ProgressStatsEngine.getTodayStats()

    Column {
        Text("Today's Checklist (${stats.completionPercent}%)")
        Spacer(Modifier.height(8.dp))

        LazyColumn {
            items(blocks) { block ->
                ChecklistItem(
                    block = block,
                    onComplete = {
                        ProgressEngine.markComplete(context, block.id)
                        update(ProgressRepository.getTodayBlocks())
                    },
                    onIncomplete = {
                        ProgressEngine.markIncomplete(context, block.id)
                        update(ProgressRepository.getTodayBlocks())
                    }
                )
            }
        }
    }
}
