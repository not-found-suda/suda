package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSummaryRoute(
    onNavigateBack: () -> Unit,
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "학습 요약",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text = "뒤로",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        ReportSummaryContent(
            activeChildState = uiState.activeChildState,
            summaryState = uiState.summaryState,
            filterUiState = uiState.filterUiState,
            actions =
                ReportSummaryActions(
                    onRetryClick = viewModel::loadActiveChildProfile,
                    onSwitchChild = onSwitchChild,
                    filterActions =
                        ReportFilterActions(
                            onInputChange = viewModel::updateFilterInput,
                            onApplyClick = viewModel::applyFilter,
                            onResetClick = viewModel::resetFilter,
                        ),
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

@Composable
private fun ReportSummaryContent(
    activeChildState: ActiveChildProfileState,
    summaryState: ReportSummaryUiState,
    filterUiState: ReportFilterUiState,
    actions: ReportSummaryActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ReportSummaryIntro(activeChildState = activeChildState)
        }

        when (activeChildState) {
            ActiveChildProfileState.Loading ->
                item {
                    ReportSummaryStatusCard(message = "아이 정보를 불러오는 중...")
                }

            is ActiveChildProfileState.Selected -> {
                item {
                    ReportFilterPanel(
                        state = filterUiState,
                        config = ReportFilterPanelConfig(),
                        actions = actions.filterActions,
                    )
                }
                item {
                    ReportSummarySection(
                        summaryState = summaryState,
                        onRetryClick = actions.onRetryClick,
                    )
                }
            }

            ActiveChildProfileState.Missing ->
                item {
                    ReportSummaryActionCard(
                        message = "리포트를 보려면 아이를 먼저 선택해 주세요.",
                        buttonText = "아이 선택하기",
                        onClick = actions.onSwitchChild,
                    )
                }

            ActiveChildProfileState.NotFound ->
                item {
                    ReportSummaryActionCard(
                        message = "선택된 아이 정보를 찾을 수 없습니다.",
                        buttonText = "아이 다시 선택하기",
                        onClick = actions.onSwitchChild,
                    )
                }

            is ActiveChildProfileState.Error ->
                item {
                    ReportSummaryErrorCard(
                        message = activeChildState.message,
                        onRetryClick = actions.onRetryClick,
                    )
                }
        }
    }
}

private data class ReportSummaryActions(
    val onRetryClick: () -> Unit,
    val onSwitchChild: () -> Unit,
    val filterActions: ReportFilterActions,
)

@Composable
private fun ReportSummaryIntro(activeChildState: ActiveChildProfileState) {
    val selectedProfile = (activeChildState as? ActiveChildProfileState.Selected)?.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedProfile?.let { "${it.name}의 학습 요약" } ?: "학습 요약 리포트",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "완료한 퀴즈를 기준으로 참여도와 정답률을 정리했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportSummaryStatusCard(message: String) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppBadge(
            text = "상태",
            tone = AppBadgeTone.Neutral,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReportSummaryErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBadge(
                text = "불러오기 실패",
                tone = AppBadgeTone.Error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppSecondaryButton(
                text = "다시 시도",
                onClick = onRetryClick,
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

@Composable
private fun ReportSummaryActionCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBadge(
                text = "아이 선택",
                tone = AppBadgeTone.Warning,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppPrimaryButton(
                text = buttonText,
                onClick = onClick,
                modifier = Modifier.height(40.dp),
            )
        }
    }
}
