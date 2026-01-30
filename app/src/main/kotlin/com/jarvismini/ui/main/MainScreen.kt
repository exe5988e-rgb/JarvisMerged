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
import com.jarvismini.ui.boot.BootScreen
import com.jarvismini.ui.home.EnhancedHomeScreen
import com.jarvismini.ui.checklist.ChecklistItem
import com.jarvismini.ui.settings.SettingsScreen
import com.jarvismini.ui.debug.DebugScreen

enum class MainTab { Home, Checklist, Settings, Debug }

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var showBoot by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var blocks by remember { mutableStateOf(emptyList<ProgressBlock>()) }

    if (showBoot) {
        BootScreen(onBootComplete = { showBoot = false })
        return
    }

    // Load today's routines immediately
    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
        blocks = ProgressRepository.getTodayBlocks()
    }

    // Update checklist when tab changes
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            ProgressInitializer.registerAllBlocks(context)
            blocks = ProgressRepository.getTodayBlocks()
        }
    }

    when (selectedTab) {
        MainTab.Home -> EnhancedHomeScreen(
            onNavigateToSettings = { selectedTab = MainTab.Settings },
            onNavigateToDebug = { selectedTab = MainTab.Debug }
        )
        MainTab.Checklist -> ChecklistScreen(blocks) { blocks = it }
        MainTab.Settings -> SettingsScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.Debug -> DebugScreen(onBack = { selectedTab = MainTab.Home })
    }
}

@Composable
private fun ChecklistScreen(
    blocks: List<ProgressBlock>,
    onBlocksUpdated: (List<ProgressBlock>) -> Unit
) {
    val context = LocalContext.current
    val stats = ProgressStatsEngine.getTodayStats()

    Column {
        Text(
            "Today's Checklist (${stats.completionPercent}%)",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(12.dp))

        if (blocks.isEmpty()) {
            Text(
                "No routines scheduled for today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(blocks) { block ->
                    ChecklistItem(
                        block = block,
                        onComplete = {
                            ProgressEngine.markComplete(context, block.id)
                            onBlocksUpdated(ProgressRepository.getTodayBlocks())
                        },
                        onInccomplete = {
                            ProgressEngine.markIncomplete(context, block.id)
                            AssistantTTS.speak(
                                context,
                                "Okay, I will remind you in 30 minutes."
                            )
                            onBlocksUpdated(ProgressRepository.getTodayBlocks())
                        }
                    )
                }
            }
        }
    }
}
