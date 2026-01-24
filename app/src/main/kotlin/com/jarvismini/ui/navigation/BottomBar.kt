package com.jarvismini.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

/**
 * BottomBar Composable with two items: Chat and Checklist
 */
@Composable
fun BottomBar(
    navController: NavController,
    currentRoute: String?
) {
    NavigationBar {
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
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}
