package com.ssafy.mobile.feature.home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState

@Composable
fun HomeRoute(
    onStartLearning: () -> Unit,
    onStartConversation: () -> Unit,
    onNavigateToChildSelect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면이 다시 보일 때마다 활성 아이 정보 갱신 (예: 전환 후 돌아왔을 때)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.loadActiveChildProfile()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ActiveChildSection(
                state = uiState.activeChildState,
                onSwitchClick = onNavigateToChildSelect,
                onRetryClick = viewModel::loadActiveChildProfile,
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "SUDA",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "오늘도 즐겁게 말하고 배워볼까요?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            AppPrimaryButton(
                text = "학습 시작",
                onClick = onStartLearning,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppSecondaryButton(
                text = "소통 시작",
                onClick = onStartConversation,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActiveChildSection(
    state: ActiveChildProfileState,
    onSwitchClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is ActiveChildProfileState.Loading -> {
                Text(
                    text = "아이 정보를 불러오는 중...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is ActiveChildProfileState.Selected -> {
                val childDescription =
                    state.profile.age?.let { age ->
                        "${state.profile.name} (${age}세)"
                    } ?: state.profile.name

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = childDescription,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "아이와 함께하고 있어요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                AppSecondaryButton(
                    text = "아이 변경",
                    onClick = onSwitchClick,
                    modifier = Modifier.height(36.dp),
                )
            }

            is ActiveChildProfileState.Missing, is ActiveChildProfileState.NotFound -> {
                val message =
                    if (state is ActiveChildProfileState.NotFound) {
                        "선택된 아이 정보를 찾을 수 없습니다."
                    } else {
                        "선택된 아이가 없습니다."
                    }
                Text(
                    text = "$message 다시 선택해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppPrimaryButton(
                    text = "아이 선택하러 가기",
                    onClick = onSwitchClick,
                    modifier = Modifier.height(40.dp),
                )
            }

            is ActiveChildProfileState.Error -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppSecondaryButton(
                    text = "다시 시도",
                    onClick = onRetryClick,
                )
            }
        }
    }
}
