@file:Suppress("TooManyFunctions")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationAnalysisStatus
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationSummary
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationWordCount
import com.ssafy.mobile.feature.report.domain.model.ReportExpressionTypeCounts
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCommunicationSummaryRoute(
    onNavigateBack: () -> Unit,
    onSwitchChild: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportCommunicationSummaryViewModel = hiltViewModel(),
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
                        text = "소통 발화 분석",
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
        ReportCommunicationSummaryContent(
            activeChildState = uiState.activeChildState,
            communicationSummaryState = uiState.communicationSummaryState,
            selectedPeriod = uiState.selectedPeriod,
            onPeriodSelected = viewModel::selectPeriod,
            onRetryClick = viewModel::loadActiveChildProfile,
            onSwitchChild = onSwitchChild,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

@Composable
private fun ReportCommunicationSummaryContent(
    activeChildState: ActiveChildProfileState,
    communicationSummaryState: ReportCommunicationSummaryState,
    selectedPeriod: ReportCommunicationSummaryPeriod,
    onPeriodSelected: (ReportCommunicationSummaryPeriod) -> Unit,
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
            ReportCommunicationSummaryIntro(activeChildState = activeChildState)
        }

        when (activeChildState) {
            ActiveChildProfileState.Loading ->
                item {
                    ReportCommunicationStatusCard(message = "아이 정보를 불러오는 중...")
                }

            is ActiveChildProfileState.Selected ->
                communicationSummaryItems(
                    state = communicationSummaryState,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = onPeriodSelected,
                    onRetryClick = onRetryClick,
                )

            ActiveChildProfileState.Missing ->
                item {
                    ReportCommunicationActionCard(
                        message = "소통 분석을 보려면 아이를 먼저 선택해 주세요.",
                        buttonText = "아이 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            ActiveChildProfileState.NotFound ->
                item {
                    ReportCommunicationActionCard(
                        message = "선택된 아이 정보를 찾을 수 없습니다.",
                        buttonText = "아이 다시 선택하기",
                        onClick = onSwitchChild,
                    )
                }

            is ActiveChildProfileState.Error ->
                item {
                    ReportCommunicationErrorCard(
                        message = activeChildState.message,
                        onRetryClick = onRetryClick,
                    )
                }
        }
    }
}

private fun LazyListScope.communicationSummaryItems(
    state: ReportCommunicationSummaryState,
    selectedPeriod: ReportCommunicationSummaryPeriod,
    onPeriodSelected: (ReportCommunicationSummaryPeriod) -> Unit,
    onRetryClick: () -> Unit,
) {
    item {
        ReportCommunicationPeriodToggle(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = onPeriodSelected,
        )
    }

    when (state) {
        ReportCommunicationSummaryState.Idle,
        ReportCommunicationSummaryState.Loading,
        ->
            item {
                ReportCommunicationStatusCard(message = "소통 발화 분석을 불러오는 중...")
            }

        is ReportCommunicationSummaryState.Error ->
            item {
                ReportCommunicationErrorCard(
                    message = state.message,
                    onRetryClick = onRetryClick,
                )
            }

        is ReportCommunicationSummaryState.Success -> {
            communicationSummaryBody(
                summary = state.summary,
                selectedPeriod = selectedPeriod,
                onRetryClick = onRetryClick,
            )
        }
    }
}

private fun LazyListScope.communicationSummaryBody(
    summary: ReportCommunicationSummary,
    selectedPeriod: ReportCommunicationSummaryPeriod,
    onRetryClick: () -> Unit,
) {
    when (summary.analysisStatus) {
        ReportCommunicationAnalysisStatus.Pending,
        ReportCommunicationAnalysisStatus.Processing,
        ->
            item {
                ReportCommunicationStatusActionCard(
                    badgeText = summary.analysisStatus.toLabel(),
                    badgeTone = summary.analysisStatus.toBadgeTone(),
                    message = "세션 종료 후 분석이 진행 중이에요.\n잠시 뒤 다시 확인해 주세요.",
                    buttonText = "다시 확인",
                    onClick = onRetryClick,
                )
            }

        ReportCommunicationAnalysisStatus.Empty ->
            item {
                ReportCommunicationStatusCard(
                    message = "${selectedPeriod.label}에 분석 완료된 아이 발화가 없어요.",
                )
            }

        ReportCommunicationAnalysisStatus.Failed ->
            item {
                ReportCommunicationStatusActionCard(
                    badgeText = "분석 실패",
                    badgeTone = AppBadgeTone.Error,
                    message = "소통 발화 분석을 완료하지 못했어요.\n잠시 후 다시 확인해 주세요.",
                    buttonText = "다시 확인",
                    onClick = onRetryClick,
                )
            }

        ReportCommunicationAnalysisStatus.Completed,
        ReportCommunicationAnalysisStatus.Unknown,
        -> {
            item {
                ReportCommunicationOverviewCard(summary = summary)
            }
            item {
                ReportCommunicationFrequentWordsCard(words = summary.frequentWords)
            }
            item {
                ReportCommunicationExpressionCard(counts = summary.expressionTypeCounts)
            }
            item {
                ReportCommunicationInsightCard(summary = summary)
            }
        }
    }
}

@Composable
private fun ReportCommunicationPeriodToggle(
    selectedPeriod: ReportCommunicationSummaryPeriod,
    onPeriodSelected: (ReportCommunicationSummaryPeriod) -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportSectionTitle(
                title = "기간 조회",
                subtitle = "선택한 기간의 모든 대화를 합쳐 분석해요.",
            )
            ReportCommunicationSummaryPeriod.entries.chunked(PERIOD_TOGGLE_COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { period ->
                        ReportCommunicationPeriodButton(
                            period = period,
                            selected = selectedPeriod == period,
                            onClick = { onPeriodSelected(period) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(PERIOD_TOGGLE_COLUMNS - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCommunicationPeriodButton(
    period: ReportCommunicationSummaryPeriod,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary else colors.surfaceVariant.copy(alpha = 0.58f),
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = period.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReportCommunicationSummaryIntro(activeChildState: ActiveChildProfileState) {
    val selectedProfile = (activeChildState as? ActiveChildProfileState.Selected)?.profile
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedProfile?.let { "${it.name}의 소통 발화 분석" } ?: "소통 발화 분석",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "저장된 대화 세션의 아이 발화를 바탕으로 표현 흐름을 정리했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportCommunicationOverviewCard(summary: ReportCommunicationSummary) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBadge(
                text = summary.analysisStatus.toLabel(),
                tone = summary.analysisStatus.toBadgeTone(),
            )
            summary.generatedAt?.let { generatedAt ->
                Text(
                    text = "생성 ${generatedAt.toReportQuizSessionDateTimeLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportMetricTile(
                title = "대화 세션",
                value = "${summary.totalSessionCount}회",
                tone = ReportVisualTone.Primary,
                modifier = Modifier.weight(1f),
            )
            ReportMetricTile(
                title = "아이 발화",
                value = "${summary.totalUtteranceCount}회",
                tone = ReportVisualTone.Secondary,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ReportMetricTile(
            title = "평균 문장 길이",
            value = String.format(Locale.KOREA, "%.1f단어", summary.averageSentenceLength),
            tone = ReportVisualTone.Tertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportCommunicationFrequentWordsCard(words: List<ReportCommunicationWordCount>) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        ReportSectionTitle(
            title = "자주 말한 단어",
            subtitle = "아이 발화에서 많이 등장한 단어예요.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (words.isEmpty()) {
            Text(
                text = "아직 자주 등장한 단어가 없어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.take(MAX_WORD_COUNT).forEachIndexed { index, word ->
                    ReportCommunicationWordRow(
                        rank = index + 1,
                        word = word,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportCommunicationWordRow(
    rank: Int,
    word: ReportCommunicationWordCount,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppBadge(
            text = "$rank",
            tone = AppBadgeTone.Secondary,
        )
        Text(
            text = word.word,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${word.count}회",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportCommunicationExpressionCard(counts: ReportExpressionTypeCounts) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        ReportSectionTitle(
            title = "표현 유형",
            subtitle = "요구, 감정, 응답 같은 발화 의도를 분류했어요.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (counts.total <= 0) {
            Text(
                text = "분류된 표현 유형이 아직 없어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReportExpressionMeter(
                    title = "요구 표현",
                    count = counts.request,
                    total = counts.total,
                    tone = ReportVisualTone.Primary,
                )
                ReportExpressionMeter(
                    title = "감정 표현",
                    count = counts.emotion,
                    total = counts.total,
                    tone = ReportVisualTone.Secondary,
                )
                ReportExpressionMeter(
                    title = "응답 표현",
                    count = counts.response,
                    total = counts.total,
                    tone = ReportVisualTone.Tertiary,
                )
                ReportExpressionMeter(
                    title = "놀이 표현",
                    count = counts.play,
                    total = counts.total,
                    tone = ReportVisualTone.Success,
                )
                ReportExpressionMeter(
                    title = "질문 표현",
                    count = counts.question,
                    total = counts.total,
                    tone = ReportVisualTone.Secondary,
                )
                ReportExpressionMeter(
                    title = "기타",
                    count = counts.other,
                    total = counts.total,
                    tone = ReportVisualTone.Neutral,
                )
            }
        }
    }
}

@Composable
private fun ReportCommunicationInsightCard(summary: ReportCommunicationSummary) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        ReportSectionTitle(
            title = "통합 분석",
            subtitle = "기간 안의 완성된 분석 결과만 모아 정리했어요.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReportCommunicationLevelRow(
                title = "소통 수준",
                value = summary.communicationLevel.toCommunicationLevelLabel(),
            )
            ReportCommunicationLevelRow(
                title = "어휘 다양성",
                value = summary.vocabularyDiversityLevel.toCommunicationLevelLabel(),
            )
            ReportCommunicationLevelRow(
                title = "문장 확장",
                value = summary.sentenceExpansionLevel.toCommunicationLevelLabel(),
            )
            ReportCommunicationLevelRow(
                title = "주의 단계",
                value = summary.cautionLevel.toCautionLevelLabel(),
            )
            ReportCommunicationTextList(
                title = "강점",
                values = summary.strengths,
            )
            ReportCommunicationTextList(
                title = "도움이 필요한 부분",
                values = summary.improvementPoints,
            )
            ReportCommunicationTextList(
                title = "보호자 가이드",
                values = summary.parentGuide,
            )
            ReportCommunicationTextList(
                title = "추천 활동",
                values = summary.recommendedActivities,
            )
        }
    }
}

@Composable
private fun ReportCommunicationLevelRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppBadge(
            text = value,
            tone = AppBadgeTone.Secondary,
        )
    }
}

@Composable
private fun ReportCommunicationTextList(
    title: String,
    values: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        if (values.isEmpty()) {
            Text(
                text = "아직 정리된 내용이 없어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            values.take(REPORT_TEXT_LIST_LIMIT).forEach { value ->
                Text(
                    text = "• $value",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ReportExpressionMeter(
    title: String,
    count: Int,
    total: Int,
    tone: ReportVisualTone,
) {
    val percent =
        if (total <= 0) {
            0.0
        } else {
            count.toDouble() / total.toDouble() * PERCENT_MAX
        }
    ReportPercentMeter(
        title = title,
        value = percent,
        detail = "${count}회",
        tone = tone,
    )
}

@Composable
private fun ReportCommunicationStatusCard(message: String) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportCommunicationStatusActionCard(
    badgeText: String,
    badgeTone: AppBadgeTone,
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppBadge(
                text = badgeText,
                tone = badgeTone,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppSecondaryButton(
                text = buttonText,
                onClick = onClick,
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

@Composable
private fun ReportCommunicationErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun ReportCommunicationActionCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    ReportGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

private fun ReportCommunicationAnalysisStatus.toLabel(): String =
    when (this) {
        ReportCommunicationAnalysisStatus.Pending -> "분석 대기"
        ReportCommunicationAnalysisStatus.Processing -> "분석 중"
        ReportCommunicationAnalysisStatus.Completed -> "분석 완료"
        ReportCommunicationAnalysisStatus.Failed -> "분석 실패"
        ReportCommunicationAnalysisStatus.Empty -> "기록 없음"
        ReportCommunicationAnalysisStatus.Unknown -> "상태 확인"
    }

private fun String.toCommunicationLevelLabel(): String =
    when (uppercase(Locale.ROOT)) {
        "LOW" -> "낮음"
        "NORMAL" -> "보통"
        "HIGH" -> "높음"
        else -> "확인 중"
    }

private fun String.toCautionLevelLabel(): String =
    when (uppercase(Locale.ROOT)) {
        "WATCH" -> "관찰 필요"
        "CONSULT" -> "상담 권장"
        else -> "특이 없음"
    }

private fun ReportCommunicationAnalysisStatus.toBadgeTone(): AppBadgeTone =
    when (this) {
        ReportCommunicationAnalysisStatus.Pending -> AppBadgeTone.Warning
        ReportCommunicationAnalysisStatus.Processing -> AppBadgeTone.Primary
        ReportCommunicationAnalysisStatus.Completed -> AppBadgeTone.Success
        ReportCommunicationAnalysisStatus.Failed -> AppBadgeTone.Error
        ReportCommunicationAnalysisStatus.Empty -> AppBadgeTone.Neutral
        ReportCommunicationAnalysisStatus.Unknown -> AppBadgeTone.Neutral
    }

private const val MAX_WORD_COUNT = 5
private const val REPORT_TEXT_LIST_LIMIT = 5
private const val PERIOD_TOGGLE_COLUMNS = 2
private const val PERCENT_MAX = 100.0
