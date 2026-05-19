@file:Suppress("MaxLineLength")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaStateView
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWord

@Composable
fun ReportSummarySection(
    summaryState: ReportSummaryUiState,
    onRetryClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (summaryState) {
            ReportSummaryUiState.Idle,
            ReportSummaryUiState.Loading,
            ->
                ReportSummaryStatusCard(
                    mascot = SudaMascot.Loading,
                    title = "리포트 요약을 불러오는 중이에요",
                )

            ReportSummaryUiState.Empty ->
                ReportSummaryStatusCard(
                    mascot = SudaMascot.Empty,
                    title = "아직 완료된 퀴즈 기록이 없어요",
                    description = "퀴즈를 마치면 이곳에 리포트가 쌓여요.",
                )

            is ReportSummaryUiState.Error ->
                ReportSummaryErrorCard(
                    message = summaryState.message,
                    onRetryClick = onRetryClick,
                )

            is ReportSummaryUiState.Success ->
                ReportSummaryContent(summary = summaryState.summary)
        }
    }
}

@Composable
private fun ReportSummaryStatusCard(
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
private fun ReportSummaryErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        SudaStateView(
            mascot = SudaMascot.ErrorNetwork,
            title = "리포트를 불러오지 못했어요",
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
private fun ReportSummaryContent(summary: ReportSummary) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SummaryMetricGrid(summary = summary)
        Spacer(modifier = Modifier.height(12.dp))
        LatestActivityCard(summary = summary)
        Spacer(modifier = Modifier.height(12.dp))
        WeakWordsPreviewCard(weakWords = summary.weakWords)
    }
}

@Composable
private fun SummaryMetricGrid(summary: ReportSummary) {
    val accuracyRateLabel =
        summary.performance.accuracyRate
            .coerceIn(
                PERCENT_MIN,
                PERCENT_MAX,
            ).toInt()
            .toString()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CleanMetricCard(
                title = "완료 퀴즈",
                value = "${summary.participation.completedSessionCount}회",
                modifier = Modifier.weight(1f),
            )
            CleanMetricCard(
                title = "정답률",
                value = "$accuracyRateLabel%",
                modifier = Modifier.weight(1f),
                content = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.height(36.dp).width(36.dp),
                    ) {
                        CircularProgressIndicator(
                            progress = {
                                (
                                    summary.performance.accuracyRate.coerceIn(
                                        PERCENT_MIN,
                                        PERCENT_MAX,
                                    ) /
                                        100.0
                                ).toFloat()
                            },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            strokeWidth = 4.dp,
                        )
                    }
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CleanMetricCard(
                title = "푼 문제",
                value = "${summary.participation.totalQuestionCount}문제",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ReportInlineStarMetric(
            title = "평균 별점",
            rating = summary.performance.averageStar,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CleanMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    ReportGlassCard(
        modifier = modifier,
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (content != null) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun LatestActivityCard(summary: ReportSummary) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            AppBadge(
                text = "최근 학습 📅",
                tone = AppBadgeTone.Primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    summary.latestActivity?.let { activity ->
                        "${activity.categoryName} · ${activity.latestSessionAt.toDateLabel()}"
                    } ?: "최근 완료한 퀴즈 기록이 없어요.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeakWordsPreviewCard(weakWords: List<ReportWeakWord>) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            AppBadge(
                text = "자주 틀리는 단어",
                tone = AppBadgeTone.Error,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (weakWords.isEmpty()) {
                Text(
                    text = "아직 자주 틀린 단어가 없어요.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "퀴즈를 더 풀면 이곳에 어려워하는 단어가 쌓여요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                weakWords.take(WEAK_WORD_PREVIEW_COUNT).forEach { word ->
                    WeakWordRow(word = word)
                }
            }
        }
    }
}

@Composable
private fun WeakWordRow(word: ReportWeakWord) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                    ),
        ) {
            Text(
                text = getWordEmoji(word.displayText ?: word.word, word.categoryName),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = word.displayText ?: word.word,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = word.categoryName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${word.wrongCount}/${word.attemptCount}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private const val WEAK_WORD_PREVIEW_COUNT = 3
private const val ISO_DATE_LENGTH = 10
private const val PERCENT_MIN = 0.0
private const val PERCENT_MAX = 100.0

private fun String?.toDateLabel(): String =
    if (isNullOrBlank()) "날짜 정보 없음" else take(ISO_DATE_LENGTH).replace("-", ".")
