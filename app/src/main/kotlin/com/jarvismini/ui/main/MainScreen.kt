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
import com.jarvismini.ui.checklist.ChecklistItem
import com.jarvismini.ui.JarvisChatScreen

enum class MainTab(val title: String) {
    Chat("Chat"),
    Checklist("Checklist")
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
    }

    var selectedTab by remember { mutableStateOf(MainTab.Chat) }
    var blocks by remember { mutableStateOf<List<ProgressBlock>>(emptyList()) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            blocks = ProgressRepository.getTodayBlocks(context)

            val stats = ProgressStatsEngine.getTodayStats(context)
            AssistantTTS.speak(
                context,
                "You have ${stats.completedBlocks} completed out of ${stats.totalBlocks} tasks today."
            )
        }
    }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MainTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
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
            when (selectedTab) {
                MainTab.Chat -> JarvisChatScreen()
                MainTab.Checklist -> ChecklistScreenWithStats(
                    blocks = blocks,
                    onBlocksUpdated = { blocks = it }
                )
            }
        }
    }
}

@Composable
private fun ChecklistScreenWithStats(
    blocks: List<ProgressBlock>,
    onBlocksUpdated: (List<ProgressBlock>) -> Unit
) {
    val context = LocalContext.current
    val stats = ProgressStatsEngine.getTodayStats(context)

    Column(modifier = Modifier.fillMaxSize()) {

        Text(
            text = "Today's Checklist (${stats.completionPercent}%)",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(blocks) { block ->
                ChecklistItem(
                    block = block,
                    onComplete = {
                        ProgressRepository.markCompleted(context, block.id)
                        onBlocksUpdated(ProgressRepository.getTodayBlocks(context))
                    },
                    onIncomplete = {
                        ProgressRepository.markIncomplete(context, block.id)
                        AssistantTTS.speak(
                            context,
                            "You missed task ${block.id.replace("_", " ").replaceFirstChar { it.uppercase() }}. I will remind you in 30 minutes."
                        )
                        onBlocksUpdated(ProgressRepository.getTodayBlocks(context))
                    }
                )
            }
        }
    }
}
