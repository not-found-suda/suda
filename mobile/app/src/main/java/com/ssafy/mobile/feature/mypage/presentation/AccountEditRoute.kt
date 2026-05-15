@file:Suppress("LongMethod")

package com.ssafy.mobile.feature.mypage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.AppTextField
import com.ssafy.mobile.core.ui.feedback.AppErrorText
import com.ssafy.mobile.feature.mypage.domain.model.AccountInfo

@Composable
fun AccountEditRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.eventMessage) {
        val message = uiState.eventMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearEventMessage()
    }

    AccountEditScreen(
        uiState = uiState,
        name = name,
        snackbarHostState = snackbarHostState,
        onNameChange = viewModel::onNameChange,
        onSave = viewModel::saveAccountInfo,
        onRetry = viewModel::loadAccountInfo,
        onBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun AccountEditScreen(
    uiState: AccountEditUiState,
    name: String,
    snackbarHostState: SnackbarHostState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "계정 정보",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(
                        onClick = onBack,
                        enabled = !uiState.isSaving,
                    ) {
                        Text("닫기")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "보호자 계정의 이름을 수정할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                uiState.isLoading -> {
                    AppLoadingIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        message = "계정 정보를 불러오고 있어요.",
                    )
                }

                uiState.accountInfo == null -> {
                    AccountEditErrorState(
                        message = uiState.errorMessage ?: "계정 정보를 불러오지 못했습니다.",
                        onRetry = onRetry,
                    )
                }

                else -> {
                    AccountEditForm(
                        accountInfo = uiState.accountInfo,
                        name = name,
                        nameError = uiState.nameError,
                        errorMessage = uiState.errorMessage,
                        isSaving = uiState.isSaving,
                        onNameChange = onNameChange,
                    )
                    AppPrimaryButton(
                        text = if (uiState.isSaving) "저장 중..." else "저장하기",
                        onClick = onSave,
                        enabled = !uiState.isSaving,
                        loading = uiState.isSaving,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountEditForm(
    accountInfo: AccountInfo,
    name: String,
    nameError: String?,
    errorMessage: String?,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        AppBadge(
            text = "보호자 계정",
            tone = AppBadgeTone.Primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        ReadOnlyAccountField(
            label = "이메일",
            value = accountInfo.email,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppTextField(
            value = name,
            onValueChange = onNameChange,
            label = "이름",
            placeholder = "예: 김보호",
            enabled = !isSaving,
            isError = nameError != null,
            supportingText = nameError ?: "이름은 50자 이하로 입력해 주세요.",
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "이메일은 로그인 계정으로 사용되어 변경할 수 없어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AppErrorText(
                message = errorMessage,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReadOnlyAccountField(
    label: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccountEditErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppErrorText(
            message = message,
            modifier = Modifier.fillMaxWidth(),
        )
        AppSecondaryButton(
            text = "다시 시도",
            onClick = onRetry,
        )
    }
}
