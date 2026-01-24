package com.jarvismini.ui.main

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.checklist.ChecklistItem
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressStatsEngine
import com.jarvismini.ProgressInitializer
import com.jarvismini.core.progress.ProgressBlock

enum class MainTab(val title: String) {
    Chat("Chat"),
    Checklist("Checklist")
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // ✅ Ensure all routine blocks are registered at app start
    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
    }

    var selectedTab by remember { mutableStateOf(MainTab.Chat) }
    var blocks by remember { mutableStateOf(listOf<ProgressBlock>()) }

    // Refresh blocks whenever tab changes
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
                MainTab.Checklist -> ChecklistScreenWithStats(blocks) { updated ->
                    blocks = updated
                }
            }
        }
    }
}

@Composable
fun ChecklistScreenWithStats(
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
                        val updated = ProgressRepository.getTodayBlocks(context)
                        onBlocksUpdated(updated)
                    },
                    onIncomplete = {
                        ProgressRepository.markIncomplete(context, block.id, block.name)
                        val updated = ProgressRepository.getTodayBlocks(context)
                        onBlocksUpdated(updated)
                    }
                )
            }
        }
    }
}
