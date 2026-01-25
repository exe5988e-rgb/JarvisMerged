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
import com.jarvismini.core.progress.ProgressBlock
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressStatsEngine
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.ChecklistItem

enum class MainTab(val title: String) {
    Chat("Chat"),
    Checklist("Checklist")
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // ✅ Register all routine blocks once
    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
    }

    var selectedTab by remember { mutableStateOf(MainTab.Chat) }
    var blocks by remember { mutableStateOf<List<ProgressBlock>>(emptyList()) }

    // ✅ Load checklist blocks when checklist tab opens
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            blocks = ProgressRepository.getTodayBlocks(context)
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
                    onBlocksUpdated = { blocks = it },
                    context = context
                )
            }
        }
    }
}

@Composable
private fun ChecklistScreenWithStats(
    blocks: List<ProgressBlock>,
    onBlocksUpdated: (List<ProgressBlock>) -> Unit,
    context: android.content.Context
) {
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
                        ProgressRepository.markCompleted(block.id)
                        onBlocksUpdated(ProgressRepository.getTodayBlocks(context))
                    },
                    onIncomplete = {
                        ProgressRepository.markIncomplete(
                            blockId = block.id,
                            blockName = block.name
                        )
                        onBlocksUpdated(ProgressRepository.getTodayBlocks(context))
                    }
                )
            }
        }
    }
}
