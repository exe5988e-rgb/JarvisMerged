package com.jarvismini.ui.main

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.checklist.DailyChecklistScreen

enum class MainTab(val title: String) {
    Chat("Chat"),
    Checklist("Checklist")
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(MainTab.Chat) }

    Scaffold(
        topBar = {
            TabRow(
                selectedTabIndex = selectedTab.ordinal
            ) {
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
        ) {
            when (selectedTab) {
                MainTab.Chat -> JarvisChatScreen()
                MainTab.Checklist -> DailyChecklistScreen()
            }
        }
    }
}
