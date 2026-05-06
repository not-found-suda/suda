package com.ssafy.mobile.core.navigation

sealed class Screen(
    val route: String,
) {
    data object AppEntry : Screen("app_entry_route")

    data object Login : Screen("login_route")

    data object Signup : Screen("signup_route")

    data object ChildSelect : Screen("child_select_route")

    data object ChildProfileCreate : Screen("child_profile_create_route")

    data object Home : Screen("home_route")

    data object Quiz : Screen("quiz_question_route")

    data object Conversation : Screen("conversation_route")

    data object MyPage : Screen("my_page_route")

    data object Sign : Screen("sign_route")

    data object LearningCategory : Screen("learning_category_route")

    data object WordList : Screen("learning_words/{categoryId}") {
        fun createRoute(categoryId: Long) = "learning_words/$categoryId"
    }
}
