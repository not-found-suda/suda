package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportQuizSessionDetailRoute(
    onNavigateBack: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportQuizSessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.loadActiveChildProfile(showLoading = false)
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
                        text = "퀴즈 기록 상세",
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
        ReportQuizSessionDetailContent(
            activeChildState = uiState.activeChildState,
            detailState = uiState.detailState,
            onRetryClick = { viewModel.loadActiveChildProfile() },
            onSwitchChild = onSwitchChild,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

@Composable
private fun ReportQuizSessionDetailContent(
    activeChildState: ActiveChildProfileState,
    detailState: ReportQuizSessionDetailState,
    onRetryClick: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ReportQuizSessionDetailIntro(activeChildState = activeChildState)
        }

        when (activeChildState) {
            ActiveChildProfileState.Loading ->
                item {
                    ReportQuizSessionDetailStatusCard(message = "아이 정보를 불러오는 중...")
                }

            is ActiveChildProfileState.Selected ->
                detailItems(
                    state = detailState,
                    onRetryClick = onRetryClick,
                )

            ActiveChildProfileState.Missing ->
                item {
                    ReportQuizSessionDetailActionCard(
                        message = "리포트를 보려면 아이를 먼저 선택해 주세요.",
                        buttonText = "아이 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            ActiveChildProfileState.NotFound ->
                item {
                    ReportQuizSessionDetailActionCard(
                        message = "선택된 아이 정보를 찾을 수 없습니다.",
                        buttonText = "아이 다시 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            is ActiveChildProfileState.Error ->
                item {
                    ReportQuizSessionDetailErrorCard(
                        message = activeChildState.message,
                        onRetryClick = onRetryClick,
                    )
                }
        }
    }
}

private fun LazyListScope.detailItems(
    state: ReportQuizSessionDetailState,
    onRetryClick: () -> Unit,
) {
    when (state) {
        ReportQuizSessionDetailState.Idle,
        ReportQuizSessionDetailState.Loading,
        ->
            item {
                ReportQuizSessionDetailStatusCard(message = "퀴즈 기록 상세를 불러오는 중...")
            }

        is ReportQuizSessionDetailState.Error ->
            item {
                ReportQuizSessionDetailErrorCard(
                    message = state.message,
                    onRetryClick = onRetryClick,
                )
            }

        is ReportQuizSessionDetailState.Success -> {
            item {
                ReportQuizSessionSummaryCard(detail = state.detail)
            }
            item {
                Text(
                    text = "문제별 답변",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (state.detail.answers.isEmpty()) {
                item {
                    ReportQuizSessionDetailStatusCard(
                        message = "아직 기록된 답변이 없어요.",
                    )
                }
            } else {
                items(
                    items = state.detail.answers,
                    key = { answer -> answer.questionId },
                ) { answer ->
                    ReportQuizAnswerCard(answer = answer)
                }
            }
        }
    }
}

@Composable
private fun ReportQuizSessionDetailIntro(activeChildState: ActiveChildProfileState) {
    val selectedProfile = (activeChildState as? ActiveChildProfileState.Selected)?.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selectedProfile?.let { "${it.name}의 퀴즈 상세" } ?: "퀴즈 기록 상세",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selectedProfile != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    painter = painterResource(id = com.ssafy.mobile.R.drawable.ic_report_boy),
                    contentDescription = "아이 아이콘",
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "퀴즈 결과와 문제별 답변을 자세히 확인할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
