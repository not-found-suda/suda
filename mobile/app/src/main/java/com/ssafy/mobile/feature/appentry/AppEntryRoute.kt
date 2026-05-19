package com.ssafy.mobile.feature.appentry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.auth.AuthState
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.feedback.AppErrorText

@Composable
fun AppEntryRoute(
    onNavigateToLogin: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToConversation: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppEntryViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Restoring -> Unit
            is AuthState.RestoreFailed -> Unit
            is AuthState.Unauthenticated -> onNavigateToLogin()
            is AuthState.AuthenticatedWithoutChild -> onNavigateToHome()
            is AuthState.AuthenticatedWithChild -> onNavigateToHome()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (authState) {
            is AuthState.Restoring -> {
                RestoringContent()
            }

            is AuthState.RestoreFailed -> {
                RestoreFailedContent(
                    onRetry = viewModel::retryRestoreSession,
                    onNavigateToLogin = onNavigateToLogin,
                    onNavigateToConversation = onNavigateToConversation,
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun RestoringContent(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "SUDA",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
        )
        Spacer(modifier = Modifier.height(28.dp))
        AppLoadingIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            message = "소중한 우리 아이와의 대화를 준비 중입니다.",
        )
    }
}

@Composable
private fun RestoreFailedContent(
    onRetry: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "로그인 정보를\n불러오지 못했습니다",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            AppErrorText(
                message =
                    "일시적인 오류이거나 기기 보안 설정 문제일 수 있습니다.\n" +
                        "다시 시도하거나 로그인 화면으로 이동해 주세요.",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(28.dp))
            AppPrimaryButton(
                text = "다시 시도하기",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppSecondaryButton(
                text = "로그인 화면으로 이동",
                onClick = onNavigateToLogin,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppSecondaryButton(
                text = "로그인 없이 소통 시작하기",
                onClick = onNavigateToConversation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
