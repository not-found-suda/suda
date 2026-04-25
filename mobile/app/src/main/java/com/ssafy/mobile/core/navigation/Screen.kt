package com.ssafy.mobile.core.navigation

sealed class Screen(
    val route: String,
) {
    data object Conversation : Screen("conversation_route")

    data object Sign : Screen("sign_route")
}
