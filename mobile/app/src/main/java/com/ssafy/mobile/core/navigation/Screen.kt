package com.ssafy.mobile.core.navigation

import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY

sealed class Screen(
    val route: String,
) {
    data object AppEntry : Screen("app_entry_route")

    data object Login : Screen("login_route")

    data object Signup : Screen("signup_route")

    data object ChildSelect : Screen("child_select_route")

    data object ChildProfileCreate : Screen("child_profile_create_route")

    data object ChildProfileEdit : Screen("child_profile_edit_route/{childId}") {
        fun createRoute(childId: Long) = "child_profile_edit_route/$childId"
    }

    data object Home : Screen("home_route")

    data object Quiz : Screen("quiz_question_route/{categoryId}?difficulty={difficulty}") {
        fun createRoute(
            categoryId: Long,
            difficulty: String = DEFAULT_LEARNING_DIFFICULTY,
        ) = "quiz_question_route/$categoryId?difficulty=${android.net.Uri.encode(difficulty)}"
    }

    data object QuizResult :
        Screen("quiz_result/{sessionId}?categoryId={categoryId}&difficulty={difficulty}") {
        fun createRoute(
            sessionId: Long,
            categoryId: Long,
            difficulty: String,
        ) = "quiz_result/$sessionId?categoryId=$categoryId" +
            "&difficulty=${android.net.Uri.encode(difficulty)}"
    }

    data object Conversation : Screen("conversation_route")

    data object MyPage : Screen("my_page_route")

    data object AccountEdit : Screen("account_edit_route")

    data object Sign : Screen("sign_route")

    data object LearningCategory : Screen("learning_category_route")

    data object WordList :
        Screen("learning_words/{categoryId}?categoryName={categoryName}&difficulty={difficulty}") {
        fun createRoute(
            categoryId: Long,
            categoryName: String,
            difficulty: String = DEFAULT_LEARNING_DIFFICULTY,
        ) = "learning_words/$categoryId?categoryName=${android.net.Uri.encode(categoryName)}" +
            "&difficulty=${android.net.Uri.encode(difficulty)}"
    }

    data object ReportHome : Screen("report_home_route")

    data object ReportSummary : Screen("report_summary_route")

    data object ReportWeakWords : Screen("report_weak_words_route")

    data object ReportCategoryProgress : Screen("report_category_progress_route")

    data object ReportQuizSessions : Screen("report_quiz_sessions_route")

    data object ReportQuizSessionDetail : Screen("report_quiz_session_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "report_quiz_session_detail/$sessionId"
    }
}
