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
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.checklist.ChecklistItem
import com.jarvismini.core.progress.ProgressRepository
import com.jarvismini.core.progress.ProgressStatsEngine
import com.jarvismini.core.progress.ProgressBlock

enum class MainTab {
    Chat, Checklist
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    // ✅ Auto-register routine blocks at app start
    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
    }

    var selectedTab by remember { mutableStateOf(MainTab.Chat) }
    var blocks by remember { mutableStateOf(listOf<ProgressBlock>()) }
    var checklistPercent by remember { mutableStateOf(0) }

    // Refresh blocks whenever checklist tab is selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            blocks = ProgressRepository.getTodayBlocks(context)
            checklistPercent = ProgressStatsEngine.getTodayStats(context).completionPercent
        }
    }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                val stats = ProgressStatsEngine.getTodayStats(context)
                val tabTitles = listOf(
                    "Chat",
                    "Checklist (${stats.completionPercent}%)"
                )

                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab.ordinal == index,
                        onClick = { selectedTab = MainTab.values()[index] },
                        text = { Text(title) }
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
                    // Update percentage dynamically
                    checklistPercent = ProgressStatsEngine.getTodayStats(context).completionPercent
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
