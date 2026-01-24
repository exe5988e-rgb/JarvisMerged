package com.jarvismini.ui.main

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jarvismini.ui.chat.ChatScreen
import com.jarvismini.ui.checklist.ChecklistScreen

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
        when (selectedTab) {
            MainTab.Chat -> ChatScreen()
            MainTab.Checklist -> ChecklistScreen()
        }
    }
}
