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
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWordPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportWeakWordsRoute(
    onNavigateBack: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportWeakWordsViewModel = hiltViewModel(),
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
                        text = "자주 틀리는 단어",
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
        ReportWeakWordsContent(
            activeChildState = uiState.activeChildState,
            weakWordsState = uiState.weakWordsState,
            filterUiState = uiState.filterUiState,
            actions =
                ReportWeakWordsActions(
                    onRetryClick = viewModel::loadActiveChildProfile,
                    onLoadMoreClick = viewModel::loadMoreWeakWords,
                    onSwitchChild = onSwitchChild,
                    filterActions =
                        ReportFilterActions(
                            onInputChange = viewModel::updateFilterInput,
                            onApplyClick = viewModel::applyFilter,
                            onResetClick = viewModel::resetFilter,
                            onRetryCategoriesClick = viewModel::loadFilterCategories,
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
private fun ReportWeakWordsContent(
    activeChildState: ActiveChildProfileState,
    weakWordsState: ReportWeakWordsState,
    filterUiState: ReportFilterUiState,
    actions: ReportWeakWordsActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ReportWeakWordsIntro(activeChildState = activeChildState)
        }

        when (activeChildState) {
            ActiveChildProfileState.Loading ->
                item {
                    ReportWeakWordsStatusCard(message = "아이 정보를 불러오는 중...")
                }

            is ActiveChildProfileState.Selected ->
                weakWordsItems(
                    state = weakWordsState,
                    filterUiState = filterUiState,
                    actions = actions,
                )

            ActiveChildProfileState.Missing ->
                item {
                    ReportWeakWordsActionCard(
                        message = "리포트를 보려면 아이를 먼저 선택해 주세요.",
                        buttonText = "아이 선택하기",
                        onClick = actions.onSwitchChild,
                    )
                }

            ActiveChildProfileState.NotFound ->
                item {
                    ReportWeakWordsActionCard(
                        message = "선택된 아이 정보를 찾을 수 없습니다.",
                        buttonText = "아이 다시 선택하기",
                        onClick = actions.onSwitchChild,
                    )
                }

            is ActiveChildProfileState.Error ->
                item {
                    ReportWeakWordsErrorCard(
                        message = activeChildState.message,
                        onRetryClick = actions.onRetryClick,
                    )
                }
        }
    }
}

private fun LazyListScope.weakWordsItems(
    state: ReportWeakWordsState,
    filterUiState: ReportFilterUiState,
    actions: ReportWeakWordsActions,
) {
    item {
        ReportFilterPanel(
            state = filterUiState,
            config =
                ReportFilterPanelConfig(
                    showCategory = true,
                    showMinAttemptCount = true,
                ),
            actions = actions.filterActions,
        )
    }

    when (state) {
        ReportWeakWordsState.Idle ->
            item {
                ReportWeakWordsStatusCard(message = "취약 단어를 불러오는 중...")
            }

        ReportWeakWordsState.Loading ->
            item {
                ReportWeakWordsStatusCard(message = "취약 단어를 불러오는 중...")
            }

        ReportWeakWordsState.Empty ->
            item {
                ReportWeakWordsStatusCard(
                    message =
                        if (filterUiState.hasAppliedFilter) {
                            "조건에 맞는 취약 단어가 없어요."
                        } else {
                            "아직 자주 틀리는 단어가 없어요.\n퀴즈를 더 풀면 어려워하는 단어를 모아볼 수 있어요."
                        },
                )
            }

        is ReportWeakWordsState.Error ->
            item {
                ReportWeakWordsErrorCard(
                    message = state.message,
                    onRetryClick = actions.onRetryClick,
                )
            }

        is ReportWeakWordsState.Success -> {
            item {
                ReportWeakWordsSummary(page = state.page)
            }
            items(
                items = state.page.words,
                key = { word -> word.wordId },
            ) { word ->
                ReportWeakWordCard(word = word)
            }
            item {
                ReportWeakWordsMoreSection(
                    state = state,
                    onLoadMoreClick = actions.onLoadMoreClick,
                )
            }
        }
    }
}

private data class ReportWeakWordsActions(
    val onRetryClick: () -> Unit,
    val onLoadMoreClick: () -> Unit,
    val onSwitchChild: () -> Unit,
    val filterActions: ReportFilterActions,
)

@Composable
private fun ReportWeakWordsIntro(activeChildState: ActiveChildProfileState) {
    val selectedProfile = (activeChildState as? ActiveChildProfileState.Selected)?.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedProfile?.let { "${it.name}의 취약 단어" } ?: "취약 단어 리포트",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "완료한 퀴즈를 기준으로 자주 틀린 단어를 정리했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportWeakWordsSummary(page: ReportWeakWordPage) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppBadge(
            text = "취약 단어",
            tone = AppBadgeTone.Error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "총 ${page.totalElements}개의 취약 단어가 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportWeakWordsMoreSection(
    state: ReportWeakWordsState.Success,
    onLoadMoreClick: () -> Unit,
) {
    val hasMore = state.page.page < state.page.totalPages

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.loadMoreErrorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (hasMore) {
            AppSecondaryButton(
                text = if (state.isLoadingMore) "불러오는 중..." else "더 보기",
                onClick = onLoadMoreClick,
                enabled = !state.isLoadingMore,
                modifier = Modifier.height(40.dp),
            )
        }
    }
}

@Composable
private fun ReportWeakWordsStatusCard(message: String) {
    AppCard(
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
private fun ReportWeakWordsErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    AppCard(
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
private fun ReportWeakWordsActionCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    AppCard(
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
