package com.jarvismini.ui.navigation

/**
 * NavRoute sealed class for type-safe Compose navigation
 */
sealed class NavRoute(val route: String) {

    object Chat : NavRoute("chat")

    object Checklist : NavRoute("checklist")
}
