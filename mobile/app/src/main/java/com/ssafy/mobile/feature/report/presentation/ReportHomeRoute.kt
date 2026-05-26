@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
)

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaStateView
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile
import com.ssafy.mobile.feature.report.domain.model.ReportSummary

@Composable
@Suppress("LongParameterList")
fun ReportHomeRoute(
    onNavigateToSummary: () -> Unit,
    onNavigateToCommunication: () -> Unit,
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
                    viewModel.loadActiveChildProfile(showLoading = false)
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
                onNavigateToCommunication = onNavigateToCommunication,
                onNavigateToWeakWords = onNavigateToWeakWords,
                onNavigateToCategoryProgress = onNavigateToCategoryProgress,
                onNavigateToQuizSessions = onNavigateToQuizSessions,
                onSwitchChild = onSwitchChild,
                onRetryClick = { viewModel.loadActiveChildProfile() },
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
    val onNavigateToCommunication: () -> Unit,
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
            val isMenuEnabled = activeChildState is ActiveChildProfileState.Selected

            if (isMenuEnabled) {
                var isFilterExpanded by rememberSaveable { mutableStateOf(false) }
                var hasInitialized by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(hasInitialized) {
                    if (!hasInitialized) {
                        val allInput =
                            filterUiState.input.applyQuickDateRange(
                                ReportQuickDateRange.All,
                                filterUiState.anchorDate,
                            )
                        actions.filterActions.onInputChange(allInput)
                        actions.filterActions.onApplyClick()
                        hasInitialized = true
                    }
                }

                val currentRange =
                    filterUiState.input.selectedQuickDateRange(
                        filterUiState.anchorDate,
                    )
                val filterStatusText =
                    when (currentRange) {
                        ReportQuickDateRange.CurrentWeek -> "이번주 · 적용 중"
                        ReportQuickDateRange.Recent30Days -> "이번달 · 적용 중"
                        ReportQuickDateRange.All -> "전체 · 적용 중"
                        null -> "직접 설정 · 적용 중"
                    }

                ReportGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { isFilterExpanded = !isFilterExpanded },
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isFilterExpanded) "조회 기간 ▲" else "조회 기간 ▼",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = filterStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (isFilterExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ReportFilterPanel(
                        state = filterUiState,
                        config = ReportFilterPanelConfig(),
                        actions =
                            actions.filterActions.copy(
                                onResetClick = {
                                    val allInput =
                                        filterUiState.input.applyQuickDateRange(
                                            ReportQuickDateRange.All,
                                            filterUiState.anchorDate,
                                        )
                                    actions.filterActions.onInputChange(allInput)
                                    actions.filterActions.onApplyClick()
                                },
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                ReportHealthSummarySection(summaryState = summaryState)

                Spacer(modifier = Modifier.height(18.dp))

                ReportRecordSection(
                    summaryState = summaryState,
                    actions = actions,
                )
            }

            if (!isMenuEnabled) {
                ReportActiveChildSection(
                    state = activeChildState,
                    onSwitchClick = actions.onSwitchChild,
                    onRetryClick = actions.onRetryClick,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReportHealthSummarySection(summaryState: ReportSummaryUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReportSectionTitle(
            title = "이번 기간 요약",
            subtitle = "학습 상태를 핵심 숫자로 먼저 확인해요.",
        )
        when (summaryState) {
            ReportSummaryUiState.Idle,
            ReportSummaryUiState.Loading,
            ->
                ReportHealthSummaryLoadingCard()

            ReportSummaryUiState.Empty ->
                ReportHomeEmptyCard()

            is ReportSummaryUiState.Error ->
                ReportHomeErrorCard(message = summaryState.message)

            is ReportSummaryUiState.Success ->
                ReportHealthSummaryCard(summary = summaryState.summary)
        }
    }
}

@Composable
private fun ReportRecordSection(
    summaryState: ReportSummaryUiState,
    actions: ReportHomeActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReportSectionTitle(
            title = "기록",
            subtitle = "필요한 리포트를 바로 열어볼 수 있어요.",
        )
        when (summaryState) {
            ReportSummaryUiState.Idle,
            ReportSummaryUiState.Loading,
            ->
                ReportRecordLoadingGrid()

            ReportSummaryUiState.Empty ->
                ReportRecordGrid(
                    actions = actions,
                    summary = null,
                    enabled = true,
                )

            is ReportSummaryUiState.Error ->
                ReportHomeErrorCard(message = summaryState.message)

            is ReportSummaryUiState.Success ->
                ReportRecordGrid(
                    actions = actions,
                    summary = summaryState.summary,
                    enabled = true,
                )
        }
    }
}

@Composable
private fun ReportHealthSummaryCard(summary: ReportSummary) {
    val accuracy = summary.performance.accuracyRate
    val complimentText =
        when {
            accuracy < 30.0 -> "조금 더 분발해봐요!"
            accuracy < 70.0 -> "잘하고 있어요!"
            else -> "참 잘했어요!!"
        }

    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "🎯 전체 정답률",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = complimentText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        text = summary.performance.accuracyRate.toHomePercentLabel(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportHealthMetricPill(
                    icon = "",
                    label = "완료 퀴즈",
                    value = "${summary.participation.completedSessionCount}개",
                    modifier = Modifier.weight(1f),
                )
                ReportHealthMetricPill(
                    icon = "",
                    label = "평균 별점",
                    value = "",
                    modifier = Modifier.weight(1f),
                    content = {
                        ReportStarRating(
                            rating = summary.performance.averageStar,
                        )
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportHealthMetricPill(
                    icon = "",
                    label = "취약 단어",
                    value = "${summary.weakWords.size}개",
                    modifier = Modifier.weight(1f),
                )
                ReportHealthMetricPill(
                    icon = "",
                    label = "학습 단어",
                    value = "${summary.participation.totalQuestionCount}개",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReportHealthSummaryLoadingCard() {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        AppLoadingIndicator(
            message = "요약을 불러오는 중...",
            modifier = Modifier.height(110.dp),
        )
    }
}

@Composable
private fun ReportHealthMetricPill(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val titleText = if (icon.isBlank()) label else "$icon $label"
            Text(
                text = titleText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (content != null) {
                content()
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReportRecordGrid(
    actions: ReportHomeActions,
    summary: ReportSummary?,
    enabled: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ReportRecordGridItem(
            title = "학습 요약 리포트",
            description =
                summary
                    ?.latestActivity
                    ?.categoryName
                    ?.let { "최근 학습 · $it" }
                    ?: "완료한 퀴즈 기준 성과를 정리했어요.",
            valueText = summary?.performance?.accuracyRate?.toHomePercentLabel() ?: "-",
            iconEmoji = "📊",
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            onClick = actions.onNavigateToSummary,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        ReportRecordGridItem(
            title = "자주 틀리는 단어 반복 학습하기",
            description = "반복 학습이 필요한 단어를 모아봤어요.",
            valueText = summary?.weakWords?.size?.let { "${it}개" } ?: "-",
            iconEmoji = "🤔",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            onClick = actions.onNavigateToWeakWords,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            iconDrawableRes = com.ssafy.mobile.R.drawable.ic_report_reading_boy,
        )
        ReportRecordGridItem(
            title = "카테고리별 학습 진행도",
            description = "카테고리별 학습 흐름을 정리해드릴게요.",
            valueText = "${summary?.participation?.completedSessionCount ?: 0}회",
            iconEmoji = "🧩",
            containerColor = Color(0xFFE8F5E9),
            onClick = actions.onNavigateToCategoryProgress,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        ReportRecordGridItem(
            title = "퀴즈 기록",
            description = "아이가 풀어본 퀴즈 기록을 최신순으로 볼 수 있어요.",
            valueText = "${summary?.participation?.totalQuestionCount ?: 0}문제",
            iconEmoji = "🏆",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            onClick = actions.onNavigateToQuizSessions,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportRecordLoadingGrid() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(4) {
            ReportRecordLoadingGridItem(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReportRecordLoadingGridItem(modifier: Modifier = Modifier) {
    ReportGlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                ) {}
                Box(
                    modifier =
                        Modifier
                            .width(48.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                            ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier =
                        Modifier
                            .width(80.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Box(
                    modifier =
                        Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(40.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun ReportHomeEmptyCard() {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        SudaStateView(
            mascot = SudaMascot.Empty,
            title = "아직 리포트가 없어요",
            description = "완료한 퀴즈 기록이 생기면 카드가 채워져요.",
            modifier = Modifier.height(132.dp),
            compact = true,
        )
    }
}

@Composable
private fun ReportHomeErrorCard(message: String) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        SudaStateView(
            mascot = SudaMascot.ErrorNetwork,
            title = "리포트를 불러오지 못했어요",
            description = message,
            modifier = Modifier.height(132.dp),
            compact = true,
        )
    }
}

@Composable
private fun ReportHeader() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = "리포트",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReportActiveChildSection(
    state: ActiveChildProfileState,
    onSwitchClick: () -> Unit,
    onRetryClick: () -> Unit,
) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                is ActiveChildProfileState.Loading -> {
                    AppLoadingIndicator(
                        message = "아이 정보를 불러오는 중...",
                        modifier = Modifier.height(112.dp),
                    )
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
                    SudaStateView(
                        mascot = SudaMascot.ErrorNetwork,
                        title = "아이 정보를 불러오지 못했어요",
                        description = state.message,
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

        AppSecondaryButton(
            text = "아이 변경",
            onClick = onSwitchClick,
            modifier =
                Modifier
                    .width(96.dp)
                    .height(40.dp),
        )
    }
}

@Composable
private fun ActiveChildMissingContent(onSwitchClick: () -> Unit) {
    SudaStateView(
        mascot = SudaMascot.Report,
        title = "아이를 먼저 선택해 주세요",
        description = "리포트를 보려면 아이 정보가 필요해요.",
        action = {
            AppPrimaryButton(
                text = "아이 선택하기",
                onClick = onSwitchClick,
                modifier = Modifier.height(40.dp),
            )
        },
    )
}

@Composable
private fun ActiveChildNotFoundContent(onSwitchClick: () -> Unit) {
    SudaStateView(
        mascot = SudaMascot.Report,
        title = "아이 정보를 찾을 수 없어요",
        description = "다시 선택하면 리포트를 볼 수 있어요.",
        action = {
            AppPrimaryButton(
                text = "아이 다시 선택하기",
                onClick = onSwitchClick,
                modifier = Modifier.height(40.dp),
            )
        },
    )
}

private const val DISABLED_CARD_ALPHA = 0.5f

@Composable
private fun ReportRecordGridItem(
    title: String,
    description: String,
    valueText: String,
    iconEmoji: String,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    iconDrawableRes: Int? = null,
) {
    val contentAlpha = if (enabled) 1f else DISABLED_CARD_ALPHA

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(contentAlpha),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (iconDrawableRes != null) {
                            Image(
                                painter = painterResource(id = iconDrawableRes),
                                contentDescription = "아이콘",
                                modifier = Modifier.size(30.dp),
                            )
                        } else {
                            Text(
                                text = iconEmoji,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun Double.toHomePercentLabel(): String = "${coerceIn(0.0, 100.0).toInt()}%"
