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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgressPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCategoryProgressRoute(
    onNavigateBack: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportCategoryProgressViewModel = hiltViewModel(),
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
                        text = "카테고리별 진행도",
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
        ReportCategoryProgressContent(
            activeChildState = uiState.activeChildState,
            categoryProgressState = uiState.categoryProgressState,
            filterUiState = uiState.filterUiState,
            onRetryClick = viewModel::loadActiveChildProfile,
            onSwitchChild = onSwitchChild,
            filterActions =
                ReportFilterActions(
                    onInputChange = viewModel::updateFilterInput,
                    onApplyClick = viewModel::applyFilter,
                    onResetClick = viewModel::resetFilter,
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

@Composable
private fun ReportCategoryProgressContent(
    activeChildState: ActiveChildProfileState,
    categoryProgressState: ReportCategoryProgressState,
    filterUiState: ReportFilterUiState,
    onRetryClick: () -> Unit,
    onSwitchChild: () -> Unit,
    filterActions: ReportFilterActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ReportCategoryProgressIntro(activeChildState = activeChildState)
        }

        when (activeChildState) {
            ActiveChildProfileState.Loading ->
                item {
                    ReportCategoryProgressStatusCard(message = "아이 정보를 불러오는 중...")
                }

            is ActiveChildProfileState.Selected ->
                categoryProgressItems(
                    state = categoryProgressState,
                    filterUiState = filterUiState,
                    filterActions = filterActions,
                    onRetryClick = onRetryClick,
                )

            ActiveChildProfileState.Missing ->
                item {
                    ReportCategoryProgressActionCard(
                        message = "리포트를 보려면 아이를 먼저 선택해 주세요.",
                        buttonText = "아이 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            ActiveChildProfileState.NotFound ->
                item {
                    ReportCategoryProgressActionCard(
                        message = "선택된 아이 정보를 찾을 수 없습니다.",
                        buttonText = "아이 다시 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            is ActiveChildProfileState.Error ->
                item {
                    ReportCategoryProgressErrorCard(
                        message = activeChildState.message,
                        onRetryClick = onRetryClick,
                    )
                }
        }
    }
}

private fun LazyListScope.categoryProgressItems(
    state: ReportCategoryProgressState,
    filterUiState: ReportFilterUiState,
    filterActions: ReportFilterActions,
    onRetryClick: () -> Unit,
) {
    item {
        ReportFilterPanel(
            state = filterUiState,
            config = ReportFilterPanelConfig(),
            actions = filterActions,
        )
    }

    when (state) {
        ReportCategoryProgressState.Idle,
        ReportCategoryProgressState.Loading,
        ->
            item {
                ReportCategoryProgressStatusCard(message = "카테고리별 리포트를 불러오는 중...")
            }

        ReportCategoryProgressState.Empty ->
            item {
                ReportCategoryProgressStatusCard(
                    message =
                        if (filterUiState.hasAppliedFilter) {
                            "조건에 맞는 카테고리 기록이 없어요."
                        } else {
                            "아직 완료된 퀴즈 기록이 있는 카테고리가 없어요.\n퀴즈를 마치면 진행도를 확인할 수 있어요."
                        },
                )
            }

        is ReportCategoryProgressState.Error ->
            item {
                ReportCategoryProgressErrorCard(
                    message = state.message,
                    onRetryClick = onRetryClick,
                )
            }

        is ReportCategoryProgressState.Success -> {
            item {
                ReportCategoryProgressSummary(page = state.page)
            }
            items(
                items = state.page.categories,
                key = { category -> category.categoryId },
            ) { category ->
                ReportCategoryProgressCard(category = category)
            }
        }
    }
}

@Composable
private fun ReportCategoryProgressIntro(activeChildState: ActiveChildProfileState) {
    val selectedProfile = (activeChildState as? ActiveChildProfileState.Selected)?.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedProfile?.let { "${it.name}의 카테고리 진행도" } ?: "카테고리 진행도",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "완료한 퀴즈를 기준으로 카테고리별 출제 범위와 정답 흐름을 정리했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportCategoryProgressSummary(page: ReportCategoryProgressPage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = CATEGORY_SUMMARY_ALPHA),
    ) {
        Text(
            text = "완료된 퀴즈 기록이 있는 ${page.categories.size}개 카테고리를 보여드려요.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ReportCategoryProgressStatusCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CATEGORY_STATUS_ALPHA),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReportCategoryProgressErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = CATEGORY_ERROR_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
private fun ReportCategoryProgressActionCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = CATEGORY_ACTION_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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

private const val CATEGORY_SUMMARY_ALPHA = 0.35f
private const val CATEGORY_STATUS_ALPHA = 0.5f
private const val CATEGORY_ERROR_ALPHA = 0.4f
private const val CATEGORY_ACTION_ALPHA = 0.35f
