package com.ssafy.mobile.core.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ssafy.mobile.feature.conversation.presentation.ConversationRoute
import com.ssafy.mobile.feature.sign.presentation.SignRecognitionScreen

@Composable
fun MobileNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Conversation.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.Conversation.route) {
            ConversationRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Screen.Sign.route) {
            SignRecognitionScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
