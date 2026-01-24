package com.jarvismini.ui.navigation

import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

@Composable
fun BottomBar(
    navController: NavController,
    currentRoute: String?
) {
    BottomNavigation {
        BottomBarItem(
            label = "Chat",
            icon = Icons.Filled.ChatBubble,
            selected = currentRoute == NavRoute.Chat.route
        ) {
            navController.navigate(NavRoute.Chat.route) {
                popUpTo(NavRoute.Chat.route) { inclusive = false }
                launchSingleTop = true
            }
        }

        BottomBarItem(
            label = "Checklist",
            icon = Icons.Filled.ListAlt,
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
    BottomNavigationItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}
