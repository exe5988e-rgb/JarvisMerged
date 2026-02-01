package com.jarvismini.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.jarvismini.ProgressInitializer
import com.jarvismini.core.progress.*
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.ui.boot.BootScreen
import com.jarvismini.ui.home.EnhancedHomeScreen
import com.jarvismini.ui.chat.JarvisChatScreen
import com.jarvismini.ui.checklist.JarvisChecklistScreen
import com.jarvismini.ui.calendar.CalendarViewModel
import com.jarvismini.ui.calendar.DayCalendarScreen
import com.jarvismini.ui.settings.SettingsScreen
import com.jarvismini.ui.debug.DebugScreen

enum class MainTab {
    Home,
    Chat,
    Calendar,
    Checklist,
    Settings,
    Debug
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var showBoot by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(MainTab.Home) }

    var blocks by remember { mutableStateOf(emptyList<ProgressBlock>()) }
    var routines by remember { mutableStateOf(emptyList<com.jarvismini.core.routine.model.Routine>()) }

    // ================= BOOT =================
    if (showBoot) {
        BootScreen(onBootComplete = { showBoot = false })
        return
    }

    // ================= INITIAL LOAD =================
    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
        blocks = ProgressRepository.getTodayBlocks()
        routines = RoutineProvider.getAllRoutines(context)
    }

    // ================= REFRESH CHECKLIST =================
    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            ProgressInitializer.registerAllBlocks(context)
            blocks = ProgressRepository.getTodayBlocks()
            routines = RoutineProvider.getAllRoutines(context)
        }
    }

    // ================= ROUTER =================
    when (selectedTab) {

        // -------- HOME --------
        MainTab.Home -> EnhancedHomeScreen(
            onNavigateToChat = { selectedTab = MainTab.Chat },
            onNavigateToCalendar = { selectedTab = MainTab.Calendar },
            onNavigateToSettings = { selectedTab = MainTab.Settings },
            onNavigateToDebug = { selectedTab = MainTab.Debug }
        )

        // -------- CHAT --------
        MainTab.Chat -> {
            JarvisChatScreen()
        }

        // -------- CALENDAR --------
        MainTab.Calendar -> {
            val vm = remember { CalendarViewModel(context) }
            DayCalendarScreen(viewModel = vm)
        }

        // -------- CHECKLIST --------
        MainTab.Checklist -> {
            JarvisChecklistScreen(
                blocks = blocks,
                routines = routines,
                onBlocksUpdated = { blocks = it }
            )
        }

        // -------- SETTINGS --------
        MainTab.Settings -> {
            SettingsScreen(onBack = { selectedTab = MainTab.Home })
        }

        // -------- DEBUG --------
        MainTab.Debug -> {
            DebugScreen(onBack = { selectedTab = MainTab.Home })
        }
    }
}
