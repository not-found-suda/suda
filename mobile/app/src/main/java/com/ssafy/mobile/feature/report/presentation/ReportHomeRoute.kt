@file:Suppress("LongParameterList", "MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppLoadingIndicator
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaStateView
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.childprofile.domain.model.ChildProfile
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import java.util.Locale

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
                ReportFilterPanel(
                    state = filterUiState,
                    config = ReportFilterPanelConfig(),
                    actions = actions.filterActions,
                )

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
                ReportRecordLoadingList()

            ReportSummaryUiState.Empty ->
                ReportRecordList(
                    actions = actions,
                    summary = null,
                    enabled = true,
                )

            is ReportSummaryUiState.Error ->
                ReportHomeErrorCard(message = summaryState.message)

            is ReportSummaryUiState.Success ->
                ReportRecordList(
                    actions = actions,
                    summary = summaryState.summary,
                    enabled = true,
                )
        }
    }
}

@Composable
private fun ReportHealthSummaryCard(summary: ReportSummary) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "정답률",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = summary.performance.accuracyRate.toHomePercentLabel(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AppBadge(
                    text = summary.performance.accuracyRate.toHomeGradeLabel(),
                    tone = summary.performance.accuracyRate.toReportBadgeTone(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportHealthMetricPill(
                    label = "완료 퀴즈",
                    value = "${summary.participation.completedSessionCount}개",
                    modifier = Modifier.weight(1f),
                )
                ReportHealthMetricPill(
                    label = "평균 별점",
                    value = summary.performance.averageStar.toStarLabel(),
                    modifier = Modifier.weight(1f),
                )
                ReportHealthMetricPill(
                    label = "취약 단어",
                    value = "${summary.weakWords.size}개",
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
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReportRecordDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun ReportRecordList(
    actions: ReportHomeActions,
    summary: ReportSummary?,
    enabled: Boolean,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        ReportRecordRow(
            title = "학습 요약 리포트",
            description =
                summary
                    ?.latestActivity
                    ?.categoryName
                    ?.let { "최근 학습 · $it" }
                    ?: "완료한 퀴즈 기준 성과를 정리했어요.",
            badgeText = "요약",
            badgeTone = AppBadgeTone.Primary,
            valueText = summary?.performance?.accuracyRate?.toHomePercentLabel() ?: "-",
            statusText = summary?.performance?.accuracyRate?.toHomeGradeLabel() ?: "대기",
            onClick = actions.onNavigateToSummary,
            enabled = enabled,
        )
        ReportRecordDivider()
        ReportRecordRow(
            title = "소통 분석",
            description = "대화에서 자주 쓴 말과 표현 흐름을 정리해요.",
            badgeText = "소통",
            badgeTone = AppBadgeTone.Success,
            valueText = "보기",
            statusText = "연결됨",
            onClick = actions.onNavigateToCommunication,
            enabled = enabled,
        )
        ReportRecordDivider()
        ReportRecordRow(
            title = "자주 틀리는 단어",
            description = "반복 학습이 필요한 단어를 모아봤어요.",
            badgeText = "단어",
            badgeTone = AppBadgeTone.Error,
            valueText = summary?.weakWords?.size?.let { "${it}개" } ?: "-",
            statusText = if (summary?.weakWords.isNullOrEmpty()) "양호" else "확인",
            onClick = actions.onNavigateToWeakWords,
            enabled = enabled,
        )
        ReportRecordDivider()
        ReportRecordRow(
            title = "카테고리별 학습 진행도",
            description = "카테고리별 학습 흐름을 정리해드릴게요.",
            badgeText = "진행",
            badgeTone = AppBadgeTone.Secondary,
            valueText = "${summary?.participation?.completedSessionCount ?: 0}회",
            statusText = "퀴즈",
            onClick = actions.onNavigateToCategoryProgress,
            enabled = enabled,
        )
        ReportRecordDivider()
        ReportRecordRow(
            title = "퀴즈 기록",
            description = "아이가 풀어본 퀴즈 기록을 최신순으로 볼 수 있어요.",
            badgeText = "퀴즈",
            badgeTone = AppBadgeTone.Tertiary,
            valueText = "${summary?.participation?.totalQuestionCount ?: 0}문제",
            statusText = "기록",
            onClick = actions.onNavigateToQuizSessions,
            enabled = enabled,
        )
    }
}

@Composable
private fun ReportRecordLoadingList() {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        repeat(HOME_PLACEHOLDER_CARD_COUNT) { index ->
            ReportRecordLoadingRow()
            if (index != HOME_PLACEHOLDER_CARD_COUNT - 1) {
                ReportRecordDivider()
            }
        }
    }
}

@Composable
private fun ReportRecordLoadingRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        ) {}
        Spacer(modifier = Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                modifier =
                    Modifier
                        .width(140.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier =
                    Modifier
                        .width(210.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            )
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
private const val HOME_PLACEHOLDER_CARD_COUNT = 3

@Composable
private fun ReportRecordRow(
    title: String,
    description: String,
    badgeText: String,
    badgeTone: AppBadgeTone,
    valueText: String,
    statusText: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else DISABLED_CARD_ALPHA

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReportMenuIcon(
            text = badgeText,
            tone = badgeTone,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            AppBadge(
                text = statusText,
                tone = badgeTone,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Double.toHomePercentLabel(): String = "${coerceIn(0.0, 100.0).toInt()}%"

private fun Double.toHomeGradeLabel(): String =
    when {
        this >= 85.0 -> "양호"
        this >= 70.0 -> "보통"
        else -> "확인"
    }

private fun Double.toReportBadgeTone(): AppBadgeTone =
    when {
        this >= 85.0 -> AppBadgeTone.Success
        this >= 70.0 -> AppBadgeTone.Warning
        else -> AppBadgeTone.Error
    }

private fun Double.toStarLabel(): String =
    String.format(
        Locale.KOREA,
        "%.1f",
        coerceIn(STAR_MIN_VALUE, STAR_MAX_VALUE),
    )

@Composable
private fun ReportMenuIcon(
    text: String,
    tone: AppBadgeTone,
) {
    val colors =
        when (tone) {
            AppBadgeTone.Primary ->
                MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer
            AppBadgeTone.Secondary ->
                MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
            AppBadgeTone.Tertiary ->
                MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
            AppBadgeTone.Success ->
                MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer
            AppBadgeTone.Error ->
                MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
            else ->
                MaterialTheme.colorScheme.surfaceVariant to
                    MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier = Modifier.size(52.dp),
        shape = MaterialTheme.shapes.small,
        color = colors.first,
        contentColor = colors.second,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.take(2),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val STAR_MIN_VALUE = 0.0
private const val STAR_MAX_VALUE = 3.0
