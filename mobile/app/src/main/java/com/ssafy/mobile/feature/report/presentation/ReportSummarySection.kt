package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWord
import java.util.Locale

@Composable
fun ReportSummarySection(
    summaryState: ReportSummaryUiState,
    onRetryClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "요약",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (summaryState) {
            ReportSummaryUiState.Idle,
            ReportSummaryUiState.Loading,
            -> ReportSummaryStatusCard(message = "리포트 요약을 불러오는 중...")

            ReportSummaryUiState.Empty ->
                ReportSummaryStatusCard(
                    message = "아직 완료된 퀴즈 기록이 없어요.\n퀴즈를 마치면 이곳에 리포트가 쌓입니다.",
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
private fun ReportSummaryStatusCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SUMMARY_STATUS_ALPHA),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportSummaryErrorCard(
    message: String,
    onRetryClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = SUMMARY_ERROR_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
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
            SummaryMetricCard(
                title = "완료 퀴즈",
                value = "${summary.participation.completedSessionCount}회",
                modifier = Modifier.weight(1f),
            )
            SummaryMetricCard(
                title = "정답률",
                value = "$accuracyRateLabel%",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryMetricCard(
                title = "푼 문제",
                value = "${summary.participation.totalQuestionCount}문제",
                modifier = Modifier.weight(1f),
            )
            SummaryMetricCard(
                title = "평균 별점",
                value = String.format(Locale.KOREA, "%.1f/3", summary.performance.averageStar),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = SUMMARY_METRIC_ALPHA),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun LatestActivityCard(summary: ReportSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SUMMARY_LATEST_ALPHA),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "최근 학습",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    summary.latestActivity?.let { activity ->
                        "${activity.categoryName} · ${activity.latestSessionAt.toDateLabel()}"
                    } ?: "최근 완료한 퀴즈 기록이 없어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeakWordsPreviewCard(weakWords: List<ReportWeakWord>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SUMMARY_PREVIEW_ALPHA),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "자주 틀리는 단어",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (weakWords.isEmpty()) {
                Text(
                    text = "아직 자주 틀린 단어가 없어요.",
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
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = word.displayText ?: word.word,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = word.categoryName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${word.wrongCount}/${word.attemptCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private const val SUMMARY_STATUS_ALPHA = 0.5f
private const val SUMMARY_ERROR_ALPHA = 0.4f
private const val SUMMARY_METRIC_ALPHA = 0.35f
private const val SUMMARY_LATEST_ALPHA = 0.25f
private const val SUMMARY_PREVIEW_ALPHA = 0.35f
private const val WEAK_WORD_PREVIEW_COUNT = 3
private const val ISO_DATE_LENGTH = 10
private const val PERCENT_MIN = 0.0
private const val PERCENT_MAX = 100.0

private fun String?.toDateLabel(): String =
    if (isNullOrBlank()) "날짜 정보 없음" else take(ISO_DATE_LENGTH).replace("-", ".")
