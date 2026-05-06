package com.ssafy.mobile.core.navigation

sealed class Screen(
    val route: String,
) {
    data object AppEntry : Screen("app_entry_route")

    data object Login : Screen("login_route")

    data object ChildSelect : Screen("child_select_route")

    data object Home : Screen("home_route")

    data object Conversation : Screen("conversation_route")

    data object Sign : Screen("sign_route")
}
