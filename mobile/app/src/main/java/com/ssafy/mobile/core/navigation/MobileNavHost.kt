package com.ssafy.mobile.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ssafy.mobile.BuildConfig
import com.ssafy.mobile.feature.appentry.AppEntryRoute
import com.ssafy.mobile.feature.childprofile.presentation.ChildProfileEditRoute
import com.ssafy.mobile.feature.childprofile.presentation.ChildProfileSelectRoute
import com.ssafy.mobile.feature.conversation.presentation.conversationRoute
import com.ssafy.mobile.feature.home.presentation.HomeRoute
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.presentation.category.LearningCategoryRoute
import com.ssafy.mobile.feature.learning.presentation.wordlist.LearningWordListRoute
import com.ssafy.mobile.feature.login.presentation.LoginRoute
import com.ssafy.mobile.feature.mypage.presentation.MyPageRoute
import com.ssafy.mobile.feature.quiz.presentation.QuizResultRoute
import com.ssafy.mobile.feature.quiz.presentation.quizQuestionRoute
import com.ssafy.mobile.feature.report.presentation.ReportCategoryProgressRoute
import com.ssafy.mobile.feature.report.presentation.ReportHomeRoute
import com.ssafy.mobile.feature.report.presentation.ReportPlaceholderRoute
import com.ssafy.mobile.feature.report.presentation.ReportWeakWordsRoute
import com.ssafy.mobile.feature.sign.presentation.SignDebugRoute
import com.ssafy.mobile.feature.signup.presentation.SignupRoute

@Composable
@Suppress("LongMethod")
fun MobileNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.AppEntry.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.AppEntry.route) {
            AppEntryRoute(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.AppEntry.route) { inclusive = true }
                    }
                },
                onNavigateToChildSelect = {
                    navController.navigate(Screen.ChildSelect.route) {
                        popUpTo(Screen.AppEntry.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.AppEntry.route) { inclusive = true }
                    }
                },
                onNavigateToConversation = {
                    navController.navigate(Screen.Conversation.route) {
                        popUpTo(Screen.AppEntry.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Login.route) {
            LoginRoute(
                onNavigateToHome = {
                    val popTarget =
                        if (navController.previousBackStackEntry?.destination?.route ==
                            Screen.Conversation.route
                        ) {
                            Screen.Conversation.route
                        } else {
                            Screen.Login.route
                        }
                    navController.navigate(Screen.Home.route) {
                        popUpTo(popTarget) { inclusive = true }
                    }
                },
                onNavigateToChildSelect = {
                    val popTarget =
                        if (navController.previousBackStackEntry?.destination?.route ==
                            Screen.Conversation.route
                        ) {
                            Screen.Conversation.route
                        } else {
                            Screen.Login.route
                        }
                    navController.navigate(Screen.ChildSelect.route) {
                        popUpTo(popTarget) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(Screen.Signup.route)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.Signup.route) {
            SignupRoute(
                onNavigateToLogin = {
                    navController.popBackStack(Screen.Login.route, inclusive = false)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.ChildSelect.route) {
            ChildProfileSelectRoute(
                navController = navController,
                onNavigateToHome = {
                    val previousRoute = navController.previousBackStackEntry?.destination?.route
                    val poppedToReport =
                        if (previousRoute == Screen.ReportHome.route) {
                            navController.popBackStack(
                                route = Screen.ReportHome.route,
                                inclusive = false,
                            )
                        } else {
                            false
                        }

                    if (!poppedToReport) {
                        val poppedToHome =
                            navController.popBackStack(
                                route = Screen.Home.route,
                                inclusive = false,
                            )

                        if (!poppedToHome) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.ChildSelect.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                onNavigateToCreate = {
                    navController.navigate(Screen.ChildProfileCreate.route)
                },
                onNavigateToEdit = { childId ->
                    navController.navigate(Screen.ChildProfileEdit.createRoute(childId))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.ChildProfileCreate.route) {
            ChildProfileEditRoute(
                onNavigateBack = { changed ->
                    if (changed) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("child_profile_changed", true)
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(
            route = Screen.ChildProfileEdit.route,
            arguments = listOf(navArgument("childId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val childId = backStackEntry.arguments?.getLong("childId")
            ChildProfileEditRoute(
                childId = childId,
                onNavigateBack = { changed ->
                    if (changed) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("child_profile_changed", true)
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.Home.route) {
            HomeRoute(
                onStartLearning = {
                    navController.navigate(Screen.LearningCategory.route) {
                        launchSingleTop = true
                    }
                },
                onStartConversation = {
                    navController.navigate(Screen.Conversation.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToChildSelect = {
                    navController.navigate(Screen.ChildSelect.route)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(
            route = Screen.Quiz.route,
            arguments =
                listOf(
                    navArgument("categoryId") { type = NavType.LongType },
                    navArgument("difficulty") {
                        type = NavType.StringType
                        defaultValue = DEFAULT_LEARNING_DIFFICULTY
                    },
                ),
        ) {
            quizQuestionRoute(
                onNavigateToResult = { sessionId, cid, diff ->
                    navController.navigate(Screen.QuizResult.createRoute(sessionId, cid, diff)) {
                        popUpTo(Screen.Quiz.route) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(
            route = Screen.QuizResult.route,
            arguments =
                listOf(
                    navArgument("sessionId") { type = NavType.LongType },
                    navArgument("categoryId") { type = NavType.LongType },
                    navArgument("difficulty") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
            val difficulty =
                backStackEntry.arguments?.getString("difficulty")
                    ?: DEFAULT_LEARNING_DIFFICULTY

            QuizResultRoute(
                onRestartQuiz = {
                    navController.navigate(Screen.Quiz.createRoute(categoryId, difficulty)) {
                        popUpTo(Screen.QuizResult.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.LearningCategory.route) {
                        popUpTo(Screen.LearningCategory.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.Conversation.route) {
            val onOpenSignDebug: (() -> Unit)? =
                if (BuildConfig.DEBUG) {
                    { navController.navigate(Screen.Sign.route) }
                } else {
                    null
                }

            conversationRoute(
                onOpenSignDebug = onOpenSignDebug,
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.MyPage.route) {
            MyPageRoute(
                onLogoutSuccess = {
                    navController.navigate(Screen.Conversation.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (BuildConfig.DEBUG) {
            composable(Screen.Sign.route) {
                SignDebugRoute(
                    onBackToMain = {
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(Screen.LearningCategory.route) {
            LearningCategoryRoute(
                onNavigateToWordList = { categoryId, categoryName, difficulty ->
                    navController.navigate(
                        Screen.WordList.createRoute(categoryId, categoryName, difficulty),
                    )
                },
                onNavigateToQuiz = { categoryId, difficulty ->
                    navController.navigate(Screen.Quiz.createRoute(categoryId, difficulty))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(
            route = Screen.WordList.route,
            arguments =
                listOf(
                    navArgument("categoryId") { type = NavType.LongType },
                    navArgument("categoryName") {
                        type = NavType.StringType
                        nullable = true
                    },
                    navArgument("difficulty") {
                        type = NavType.StringType
                        defaultValue = DEFAULT_LEARNING_DIFFICULTY
                    },
                ),
        ) {
            LearningWordListRoute(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onStartQuiz = { categoryId, difficulty ->
                    navController.navigate(Screen.Quiz.createRoute(categoryId, difficulty))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.ReportHome.route) {
            ReportHomeRoute(
                onNavigateToSummary = {
                    navController.navigate(Screen.ReportSummary.route)
                },
                onNavigateToWeakWords = {
                    navController.navigate(Screen.ReportWeakWords.route)
                },
                onNavigateToCategoryProgress = {
                    navController.navigate(Screen.ReportCategoryProgress.route)
                },
                onSwitchChild = {
                    navController.navigate(Screen.ChildSelect.route)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.ReportSummary.route) {
            ReportPlaceholderRoute(
                title = "학습 요약",
                onNavigateBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.ReportWeakWords.route) {
            ReportWeakWordsRoute(
                onNavigateBack = { navController.popBackStack() },
                onSwitchChild = {
                    navController.navigate(Screen.ChildSelect.route)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Screen.ReportCategoryProgress.route) {
            ReportCategoryProgressRoute(
                onNavigateBack = { navController.popBackStack() },
                onSwitchChild = {
                    navController.navigate(Screen.ChildSelect.route)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
