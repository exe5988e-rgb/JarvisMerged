package com.jarvismini.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

@Composable
fun BottomBar(
    navController: NavController,
    currentRoute: String?
) {
    NavigationBar {
        BottomBarItem(
            label = "Chat",
            icon = Icons.Default.Chat,
            selected = currentRoute == NavRoute.Chat.route
        ) {
            navController.navigate(NavRoute.Chat.route) {
                popUpTo(NavRoute.Chat.route) { inclusive = false }
                launchSingleTop = true
            }
        }

        BottomBarItem(
            label = "Checklist",
            icon = Icons.Default.Checklist,
            selected = currentRoute == NavRoute.Checklist.route
        ) {
            navController.navigate(NavRoute.Checklist.route) {
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}
