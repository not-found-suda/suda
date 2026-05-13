package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile

@Composable
fun ReportHomeRoute(
    onNavigateToSummary: () -> Unit,
    onNavigateToWeakWords: () -> Unit,
    onNavigateToCategoryProgress: () -> Unit,
    onNavigateToQuizSessions: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportHomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    ReportHomeScreen(
        activeChildState = uiState.activeChildState,
        summaryState = uiState.summaryState,
        filterUiState = uiState.filterUiState,
        actions =
            ReportHomeActions(
                onNavigateToSummary = onNavigateToSummary,
                onNavigateToWeakWords = onNavigateToWeakWords,
                onNavigateToCategoryProgress = onNavigateToCategoryProgress,
                onNavigateToQuizSessions = onNavigateToQuizSessions,
                onSwitchChild = onSwitchChild,
                onRetryClick = viewModel::loadActiveChildProfile,
                filterActions =
                    ReportFilterActions(
                        onInputChange = viewModel::updateFilterInput,
                        onApplyClick = viewModel::applyFilter,
                        onResetClick = viewModel::resetFilter,
                    ),
            ),
        modifier = modifier,
    )
}

private data class ReportHomeActions(
    val onNavigateToSummary: () -> Unit,
    val onNavigateToWeakWords: () -> Unit,
    val onNavigateToCategoryProgress: () -> Unit,
    val onNavigateToQuizSessions: () -> Unit,
    val onSwitchChild: () -> Unit,
    val onRetryClick: () -> Unit,
    val filterActions: ReportFilterActions,
)

@Composable
private fun ReportHomeScreen(
    activeChildState: ActiveChildProfileState,
    summaryState: ReportSummaryUiState,
    filterUiState: ReportFilterUiState,
    actions: ReportHomeActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ReportHeader()
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            ReportActiveChildSection(
                state = activeChildState,
                onSwitchClick = actions.onSwitchChild,
                onRetryClick = actions.onRetryClick,
            )

            Spacer(modifier = Modifier.height(32.dp))

            val isMenuEnabled = activeChildState is ActiveChildProfileState.Selected

            if (isMenuEnabled) {
                ReportFilterPanel(
                    state = filterUiState,
                    config = ReportFilterPanelConfig(),
                    actions = actions.filterActions,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ReportSummarySection(
                    summaryState = summaryState,
                    onRetryClick = actions.onRetryClick,
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            Text(
                text = "리포트 목록",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // NOTE: 아래 아이콘(이모지)들은 기획 확정 전까지 사용되는 임시 시각 요소(placeholder)입니다.
            ReportMenuCard(
                title = "학습 요약 리포트",
                description = "최근 아이의 학습 성과를 한눈에 보여드려요.",
                iconEmoji = "📝",
                onClick = actions.onNavigateToSummary,
                enabled = isMenuEnabled,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ReportMenuCard(
                title = "자주 틀리는 단어",
                description = "아이가 어려워하는 단어들을 모아봤어요.",
                iconEmoji = "⚠️",
                onClick = actions.onNavigateToWeakWords,
                enabled = isMenuEnabled,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ReportMenuCard(
                title = "카테고리별 학습 진행도",
                description = "카테고리별 학습 흐름을 정리해드릴게요.",
                iconEmoji = "📊",
                onClick = actions.onNavigateToCategoryProgress,
                enabled = isMenuEnabled,
            )

            Spacer(modifier = Modifier.height(12.dp))

            ReportMenuCard(
                title = "퀴즈 기록",
                description = "아이가 풀어본 퀴즈 기록을 최신순으로 볼 수 있어요.",
                iconEmoji = "🧩",
                onClick = actions.onNavigateToQuizSessions,
                enabled = isMenuEnabled,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReportHeader() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = "리포트",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "아이의 성장 기록을 확인해 보세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportActiveChildSection(
    state: ActiveChildProfileState,
    onSwitchClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is ActiveChildProfileState.Loading -> {
                    Text(text = "아이 정보를 불러오는 중...")
                }

                is ActiveChildProfileState.Selected -> {
                    ActiveChildSelectedContent(
                        profile = state.profile,
                        onSwitchClick = onSwitchClick,
                    )
                }

                is ActiveChildProfileState.Missing -> {
                    ActiveChildMissingContent(onSwitchClick = onSwitchClick)
                }

                is ActiveChildProfileState.NotFound -> {
                    ActiveChildNotFoundContent(onSwitchClick = onSwitchClick)
                }

                is ActiveChildProfileState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppSecondaryButton(
                            text = "다시 시도",
                            onClick = onRetryClick,
                            modifier = Modifier.height(36.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveChildSelectedContent(
    profile: ChildProfile,
    onSwitchClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = profile.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile.age?.let { age ->
                Text(
                    text = "${age}세",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedButton(
            onClick = onSwitchClick,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Text(
                text = "아이 변경",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ActiveChildMissingContent(onSwitchClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "선택된 아이가 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppPrimaryButton(
            text = "아이 선택하기",
            onClick = onSwitchClick,
            modifier = Modifier.height(40.dp),
        )
    }
}

@Composable
private fun ActiveChildNotFoundContent(onSwitchClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "선택된 아이 정보를 찾을 수 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppPrimaryButton(
            text = "아이 다시 선택하기",
            onClick = onSwitchClick,
            modifier = Modifier.height(40.dp),
        )
    }
}

private const val DISABLED_CARD_ALPHA = 0.5f

@Composable
private fun ReportMenuCard(
    title: String,
    description: String,
    iconEmoji: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        val contentAlpha = if (enabled) 1f else DISABLED_CARD_ALPHA

        Row(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = iconEmoji,
                        fontSize = 24.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "▶",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            )
        }
    }
}
