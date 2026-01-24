package com.jarvismini.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jarvismini.ui.JarvisChatScreen
import com.jarvismini.ui.DailyChecklistScreen

/**
 * MainNav Composable: wires BottomBar with NavHost
 */
@Composable
fun MainNav() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val currentRoute = navController.currentDestination?.route
            BottomBar(navController = navController, currentRoute = currentRoute)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Chat.route,
            modifier = Modifier.padding(padding)
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
