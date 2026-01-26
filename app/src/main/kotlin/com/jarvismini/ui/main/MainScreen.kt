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
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.core.tts.AssistantTTS
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.checklist.ChecklistItem

enum class MainTab { Chat, Checklist }

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(MainTab.Chat) }
    var blocks by remember { mutableStateOf(emptyList<ProgressBlock>()) }

    // Load today's routines immediately
    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
        blocks = ProgressRepository.getTodayBlocks()
    }

    // Reload checklist when tab changes
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            ProgressInitializer.registerAllBlocks(context)
            blocks = ProgressRepository.getTodayBlocks()
        }
    }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MainTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.name) }
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
                MainTab.Checklist -> ChecklistScreen(blocks) { blocks = it }
            }
        }
    }
}

@Composable
private fun ChecklistScreen(
    blocks: List<ProgressBlock>,
    onBlocksUpdated: (List<ProgressBlock>) -> Unit
) {
    val context = LocalContext.current
    val routineProvider = RoutineProvider // use the object directly
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
                    val routine = routineProvider.getRoutine(block.id)  // ✅ map block -> routine
                    if (routine != null) {
                        ChecklistItem(
                            block = block,
                            routine = routine, // ✅ pass Routine
                            onComplete = {
                                ProgressEngine.markComplete(context, block.id)
                                onBlocksUpdated(ProgressRepository.getTodayBlocks())
                            },
                            onIncomplete = {
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
}
