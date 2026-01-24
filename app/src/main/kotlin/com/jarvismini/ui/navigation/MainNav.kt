package com.jarvismini.ui.navigation

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.*
import com.jarvismini.ui.DailyChecklistScreen
import com.jarvismini.ui.JarvisChatScreen

@Composable
fun MainNav() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            BottomBar(
                navController = navController,
                currentRoute = currentRoute
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Chat.route
        ) {
            composable(NavRoute.Chat.route) {
                JarvisChatScreen()
            }

            composable(NavRoute.Checklist.route) {
                DailyChecklistScreen()
            }
        }
    }
}

/**
 * Centralized route definitions
 */
sealed class NavRoute(val route: String) {
    object Chat : NavRoute("chat")
    object Checklist : NavRoute("checklist")
}
