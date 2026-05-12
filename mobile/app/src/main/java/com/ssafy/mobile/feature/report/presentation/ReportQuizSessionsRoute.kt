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
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportQuizSessionsRoute(
    onNavigateBack: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportQuizSessionsViewModel = hiltViewModel(),
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
                        text = "퀴즈 기록",
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
        ReportQuizSessionsContent(
            activeChildState = uiState.activeChildState,
            quizSessionsState = uiState.quizSessionsState,
            onRetryClick = viewModel::loadActiveChildProfile,
            onLoadMoreClick = viewModel::loadMoreQuizSessions,
            onSwitchChild = onSwitchChild,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

@Composable
private fun ReportQuizSessionsContent(
    activeChildState: ActiveChildProfileState,
    quizSessionsState: ReportQuizSessionsState,
    onRetryClick: () -> Unit,
    onLoadMoreClick: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ReportQuizSessionsIntro(activeChildState = activeChildState)
        }

        when (activeChildState) {
            ActiveChildProfileState.Loading ->
                item {
                    ReportQuizSessionsStatusCard(message = "아이 정보를 불러오는 중...")
                }

            is ActiveChildProfileState.Selected ->
                quizSessionsItems(
                    state = quizSessionsState,
                    onRetryClick = onRetryClick,
                    onLoadMoreClick = onLoadMoreClick,
                )

            ActiveChildProfileState.Missing ->
                item {
                    ReportQuizSessionsActionCard(
                        message = "리포트를 보려면 아이를 먼저 선택해 주세요.",
                        buttonText = "아이 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            ActiveChildProfileState.NotFound ->
                item {
                    ReportQuizSessionsActionCard(
                        message = "선택된 아이 정보를 찾을 수 없습니다.",
                        buttonText = "아이 다시 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            is ActiveChildProfileState.Error ->
                item {
                    ReportQuizSessionsErrorCard(
                        message = activeChildState.message,
                        onRetryClick = onRetryClick,
                    )
                }
        }
    }
}

private fun LazyListScope.quizSessionsItems(
    state: ReportQuizSessionsState,
    onRetryClick: () -> Unit,
    onLoadMoreClick: () -> Unit,
) {
    when (state) {
        ReportQuizSessionsState.Idle,
        ReportQuizSessionsState.Loading,
        ->
            item {
                ReportQuizSessionsStatusCard(message = "퀴즈 기록을 불러오는 중...")
            }

        ReportQuizSessionsState.Empty ->
            item {
                ReportQuizSessionsStatusCard(
                    message = "아직 퀴즈 기록이 없어요.\n퀴즈를 완료하면 기록을 모아볼 수 있어요.",
                )
            }

        is ReportQuizSessionsState.Error ->
            item {
                ReportQuizSessionsErrorCard(
                    message = state.message,
                    onRetryClick = onRetryClick,
                )
            }

        is ReportQuizSessionsState.Success -> {
            item {
                ReportQuizSessionsSummary(page = state.page)
            }
            items(
                items = state.page.sessions,
                key = { session -> session.sessionId },
            ) { session ->
                ReportQuizSessionCard(session = session)
            }
            item {
                ReportQuizSessionsMoreSection(
                    state = state,
                    onLoadMoreClick = onLoadMoreClick,
                )
            }
        }
    }
}

@Composable
private fun ReportQuizSessionsIntro(activeChildState: ActiveChildProfileState) {
    val selectedProfile = (activeChildState as? ActiveChildProfileState.Selected)?.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedProfile?.let { "${it.name}의 퀴즈 기록" } ?: "퀴즈 기록",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "아이의 퀴즈 풀이 기록을 최신순으로 정리했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportQuizSessionsSummary(page: ReportQuizSessionPage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = QUIZ_SUMMARY_ALPHA),
    ) {
        Text(
            text = "총 ${page.totalElements}개의 퀴즈 기록이 있어요.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ReportQuizSessionsMoreSection(
    state: ReportQuizSessionsState.Success,
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
private fun ReportQuizSessionsStatusCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = QUIZ_STATUS_ALPHA),
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
private fun ReportQuizSessionsErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = QUIZ_ERROR_ALPHA),
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
private fun ReportQuizSessionsActionCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = QUIZ_ACTION_ALPHA),
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

private const val QUIZ_SUMMARY_ALPHA = 0.35f
private const val QUIZ_STATUS_ALPHA = 0.5f
private const val QUIZ_ERROR_ALPHA = 0.4f
private const val QUIZ_ACTION_ALPHA = 0.35f
