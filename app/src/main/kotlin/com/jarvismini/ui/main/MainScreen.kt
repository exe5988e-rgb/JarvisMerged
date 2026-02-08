package com.jarvismini.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.jarvismini.ProgressInitializer
import com.jarvismini.core.progress.*
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.ui.boot.BootScreen
import com.jarvismini.ui.home.EnhancedHomeScreen
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.calendar.CalendarViewModel
import com.jarvismini.ui.calendar.DayCalendarScreen
import com.jarvismini.ui.settings.SettingsScreen
import com.jarvismini.ui.debug.DebugScreen
import com.jarvismini.ui.checklist.JarvisChecklistScreen

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

    if (showBoot) {
        BootScreen(onBootComplete = { showBoot = false })
        return
    }

    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
        blocks = ProgressRepository.getTodayBlocks()
        routines = RoutineProvider.getAllRoutines(context)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            ProgressInitializer.registerAllBlocks(context)
            blocks = ProgressRepository.getTodayBlocks()
            routines = RoutineProvider.getAllRoutines(context)
        }
    }

    when (selectedTab) {

        MainTab.Home -> EnhancedHomeScreen(
            onNavigateToChat = { selectedTab = MainTab.Chat },
            onNavigateToCalendar = { selectedTab = MainTab.Calendar },
            onNavigateToChecklist = { selectedTab = MainTab.Checklist },
            onNavigateToSettings = { selectedTab = MainTab.Settings },
            onNavigateToDebug = { selectedTab = MainTab.Debug }
        )

        MainTab.Chat -> JarvisChatScreen(onBack = { selectedTab = MainTab.Home })

        MainTab.Calendar -> {
            val vm = remember { CalendarViewModel(context) }
            DayCalendarScreen(viewModel = vm)
        }

        MainTab.Checklist -> JarvisChecklistScreen()

        MainTab.Settings -> SettingsScreen(onBack = { selectedTab = MainTab.Home })

        MainTab.Debug -> DebugScreen(onBack = { selectedTab = MainTab.Home })
    }
}
