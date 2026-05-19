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
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaStateView
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWord
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWordPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportWeakWordsRoute(
    onNavigateBack: () -> Unit,
    onSwitchChild: () -> Unit,
    onLearnWord: (ReportWeakWord) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportWeakWordsViewModel = hiltViewModel(),
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
                        text = "자주 틀리는 단어 반복 학습하기",
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
            actions =
                ReportWeakWordsActions(
                    onRetryClick = { viewModel.loadActiveChildProfile() },
                    onLoadMoreClick = viewModel::loadMoreWeakWords,
                    onSwitchChild = onSwitchChild,
                    onLearnWord = onLearnWord,
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
                    ReportWeakWordsStatusCard(
                        mascot = SudaMascot.Loading,
                        title = "아이 정보를 불러오는 중이에요",
                    )
                }

            is ActiveChildProfileState.Selected ->
                weakWordsItems(
                    state = weakWordsState,
                    actions = actions,
                )

            ActiveChildProfileState.Missing ->
                item {
                    ReportWeakWordsActionCard(
                        title = "아이를 먼저 선택해 주세요",
                        description = "리포트를 보려면 아이 정보가 필요해요.",
                        buttonText = "아이 선택하기",
                        onClick = actions.onSwitchChild,
                    )
                }

            ActiveChildProfileState.NotFound ->
                item {
                    ReportWeakWordsActionCard(
                        title = "아이 정보를 찾을 수 없어요",
                        description = "다시 선택하면 리포트를 볼 수 있어요.",
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
    actions: ReportWeakWordsActions,
) {
    when (state) {
        ReportWeakWordsState.Idle ->
            item {
                ReportWeakWordsStatusCard(
                    mascot = SudaMascot.Loading,
                    title = "취약 단어를 불러오는 중이에요",
                )
            }

        ReportWeakWordsState.Loading ->
            item {
                ReportWeakWordsStatusCard(
                    mascot = SudaMascot.Loading,
                    title = "취약 단어를 불러오는 중이에요",
                )
            }

        ReportWeakWordsState.Empty ->
            item {
                ReportWeakWordsStatusCard(
                    mascot = SudaMascot.Empty,
                    title = "이번 기간에 취약 단어가 없어요",
                    description = "퀴즈를 더 풀면 어려워하는 단어를 모아볼 수 있어요.",
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
                ReportWeakWordCard(
                    word = word,
                    onLearnClick = { actions.onLearnWord(word) },
                )
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
    val onLearnWord: (ReportWeakWord) -> Unit,
)

@Composable
private fun ReportWeakWordsIntro(activeChildState: ActiveChildProfileState) {
    val selectedProfile = (activeChildState as? ActiveChildProfileState.Selected)?.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = selectedProfile?.let { "${it.name}의 취약 단어 반복 학습하기" } ?: "취약 단어 반복 학습하기",
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
            text = "완료한 퀴즈를 기준으로 자주 틀린 단어를 정리했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportWeakWordsSummary(page: ReportWeakWordPage) {
    ReportGlassCard(
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
private fun ReportWeakWordsStatusCard(
    mascot: SudaMascot,
    title: String,
    description: String? = null,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SudaStateView(
            mascot = mascot,
            title = title,
            description = description,
            modifier = Modifier.height(132.dp),
            compact = true,
        )
    }
}

@Composable
private fun ReportWeakWordsErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SudaStateView(
            mascot = SudaMascot.ErrorNetwork,
            title = "취약 단어를 불러오지 못했어요",
            description = message,
            action = {
                AppSecondaryButton(
                    text = "다시 시도",
                    onClick = onRetryClick,
                    modifier = Modifier.height(36.dp),
                )
            },
        )
    }
}

@Composable
private fun ReportWeakWordsActionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SudaStateView(
            mascot = SudaMascot.Report,
            title = title,
            description = description,
            action = {
                AppPrimaryButton(
                    text = buttonText,
                    onClick = onClick,
                    modifier = Modifier.height(40.dp),
                )
            },
        )
    }
}
