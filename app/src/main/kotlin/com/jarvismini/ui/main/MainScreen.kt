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

    var selectedTab by remember { mutableStateOf(MainTab.Chat) }
    var blocks by remember { mutableStateOf<List<ProgressBlock>>(emptyList()) }

    // One-time hydration + registration
    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
    }

    // Reload checklist + speak stats when entering Checklist tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            blocks = ProgressStore.getTodayBlocks()

            val stats = ProgressStatsEngine.getTodayStats()
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
    val stats = ProgressStatsEngine.getTodayStats()

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

                val displayName = block.id
                    .replace("_", " ")
                    .replaceFirstChar { it.uppercase() }

                ChecklistItem(
                    block = block,
                    onComplete = {
                        ProgressEngine.markComplete(context, block.id)
                        onBlocksUpdated(ProgressStore.getTodayBlocks())
                    },
                    onIncomplete = {
                        ProgressEngine.markIncomplete(context, block.id)
                        AssistantTTS.speak(
                            context,
                            "You missed task $displayName. I will remind you in 30 minutes."
                        )
                        onBlocksUpdated(ProgressStore.getTodayBlocks())
                    }
                )
            }
        }
    }
}
