package com.jarvismini.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvismini.agent.AgentDashboardViewModel
import com.jarvismini.core.ProgressInitializer
import com.jarvismini.core.ProgressRepository
import com.jarvismini.core.routine.RoutineProvider
import com.jarvismini.core.routine.model.Routine
import com.jarvismini.ui.boot.BootScreen
import com.jarvismini.ui.calendar.DayCalendarScreen
import com.jarvismini.ui.calendar.CalendarViewModel
import com.jarvismini.ui.chat.JarvisChatScreen
import com.jarvismini.ui.checklist.JarvisChecklistScreen
import com.jarvismini.ui.debug.DebugScreen
import com.jarvismini.ui.home.EnhancedHomeScreen
import com.jarvismini.ui.llm.TermuxCommandScreen
import com.jarvismini.ui.settings.SettingsScreen
import com.jarvismini.agent.AgentDashboardScreen

enum class MainTab {
    Home,
    Chat,
    Calendar,
    Checklist,
    Settings,
    Debug,
    TermuxCommand,
    AgentDashboard,
}

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var showBoot    by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(MainTab.Home) }

    // Shared AgentDashboardViewModel so voice tasks from Home flow into the dashboard
    val agentVm: AgentDashboardViewModel = viewModel()

    if (showBoot) {
        BootScreen(onBootComplete = { showBoot = false })
        return
    }

    LaunchedEffect(Unit) {
        ProgressInitializer.registerAllBlocks(context)
        ProgressRepository.getTodayBlocks()
        RoutineProvider.getAllRoutines(context)
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == MainTab.Checklist) {
            ProgressInitializer.registerAllBlocks(context)
        }
    }

    when (selectedTab) {
        MainTab.Home -> EnhancedHomeScreen(
            onNavigateToChat          = { selectedTab = MainTab.Chat },
            onNavigateToCalendar      = { selectedTab = MainTab.Calendar },
            onNavigateToChecklist     = { selectedTab = MainTab.Checklist },
            onNavigateToSettings      = { selectedTab = MainTab.Settings },
            onNavigateToDebug         = { selectedTab = MainTab.Debug },
            onNavigateToTermuxCommand = { selectedTab = MainTab.TermuxCommand },
            onNavigateToAgent         = { selectedTab = MainTab.AgentDashboard },
            onVoiceTask               = { task ->
                // Pre-fill task in VM, navigate to dashboard, then auto-start the agent
                agentVm.onTaskInput(task)
                selectedTab = MainTab.AgentDashboard
                agentVm.startAgent()           // ← FIX: was missing — voice never triggered run
            }
        )
        MainTab.Chat         -> JarvisChatScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.Calendar     -> {
            val vm = remember { CalendarViewModel(context) }
            DayCalendarScreen(viewModel = vm, onBack = { selectedTab = MainTab.Home })
        }
        MainTab.Checklist    -> JarvisChecklistScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.Settings     -> SettingsScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.Debug        -> DebugScreen(onBack = { selectedTab = MainTab.Home })
        MainTab.TermuxCommand -> TermuxCommandScreen(onNavigateBack = { selectedTab = MainTab.Home })
        MainTab.AgentDashboard -> AgentDashboardScreen(
            onBack = { selectedTab = MainTab.Home },
            vm     = agentVm,
        )
    }
}
