package com.ssafy.mobile.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ssafy.mobile.BuildConfig
import com.ssafy.mobile.feature.appentry.AppEntryRoute
import com.ssafy.mobile.feature.conversation.presentation.conversationRoute
import com.ssafy.mobile.feature.placeholder.ChildSelectPlaceholderRoute
import com.ssafy.mobile.feature.placeholder.HomePlaceholderRoute
import com.ssafy.mobile.feature.placeholder.LoginPlaceholderRoute
import com.ssafy.mobile.feature.sign.presentation.SignDebugRoute

@Composable
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
            )
        }

        composable(Screen.Login.route) {
            LoginPlaceholderRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Screen.ChildSelect.route) {
            ChildSelectPlaceholderRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Screen.Home.route) {
            HomePlaceholderRoute(modifier = Modifier.fillMaxSize())
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
    }
}
