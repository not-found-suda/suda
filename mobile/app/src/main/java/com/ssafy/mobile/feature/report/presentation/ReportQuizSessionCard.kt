package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSession

@Composable
fun ReportQuizSessionCard(
    session: ReportQuizSession,
    onClick: () -> Unit,
) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${session.categoryName} 퀴즈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppBadge(
                            text = session.difficulty.toReportDifficultyLabel(),
                            tone = AppBadgeTone.Primary,
                        )
                        AppBadge(
                            text = session.status.toReportSessionStatusLabel(),
                            tone = session.status.toReportSessionStatusBadgeTone(),
                        )
                    }
                }
                AppBadge(
                    text = "${session.correctCount}/${session.totalQuestionCount}",
                    tone = AppBadgeTone.Neutral,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            ReportPercentMeter(
                title = "정답률",
                value = session.accuracyRate,
                detail = "${session.correctCount}/${session.totalQuestionCount}문제",
                tone = ReportVisualTone.Success,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReportQuizSessionMetricRow(session = session)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = session.toDateLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportQuizSessionMetricRow(session: ReportQuizSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportQuizSessionMetric(
            title = "정답",
            value = "${session.correctCount}개",
            tone = ReportVisualTone.Success,
            modifier = Modifier.weight(1f),
        )
        ReportQuizSessionMetric(
            title = "문제",
            value = "${session.totalQuestionCount}개",
            tone = ReportVisualTone.Tertiary,
            modifier = Modifier.weight(1f),
        )
        ReportStarMetricTile(
            title = "평균 별점",
            rating = session.averageStar,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReportQuizSessionMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: ReportVisualTone = ReportVisualTone.Primary,
) {
    ReportMetricTile(
        title = title,
        value = value,
        modifier = modifier,
        tone = tone,
    )
}

private fun ReportQuizSession.toDateLabel(): String =
    when {
        !endedAt.isNullOrBlank() -> "완료 ${endedAt.toReportQuizSessionDateLabel()}"
        !startedAt.isNullOrBlank() -> "시작 ${startedAt.toReportQuizSessionDateLabel()}"
        else -> "날짜 정보 없음"
    }
