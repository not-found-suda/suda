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
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSession
import java.util.Locale

@Composable
fun ReportQuizSessionCard(session: ReportQuizSession) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = QUIZ_SESSION_CARD_ALPHA),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                    Text(
                        text =
                            "${session.difficulty.toDifficultyLabel()} · " +
                                session.status.toStatusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${session.correctCount}/${session.totalQuestionCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
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
            modifier = Modifier.weight(1f),
        )
        ReportQuizSessionMetric(
            title = "정답률",
            value = String.format(Locale.KOREA, "%.1f%%", session.accuracyRate),
            modifier = Modifier.weight(1f),
        )
        ReportQuizSessionMetric(
            title = "평균 별점",
            value = String.format(Locale.KOREA, "%.1f/3", session.averageStar),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReportQuizSessionMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun ReportQuizSession.toDateLabel(): String =
    when {
        !endedAt.isNullOrBlank() -> "완료 ${endedAt.toReportDateLabel()}"
        !startedAt.isNullOrBlank() -> "시작 ${startedAt.toReportDateLabel()}"
        else -> "날짜 정보 없음"
    }

private fun String.toReportDateLabel(): String = take(ISO_DATE_LENGTH).replace("-", ".")

private fun String.toDifficultyLabel(): String =
    when (this) {
        "EASY" -> "쉬움"
        "NORMAL" -> "보통"
        "HARD" -> "어려움"
        else -> this
    }

private fun String.toStatusLabel(): String =
    when (this) {
        "COMPLETED" -> "완료"
        "STARTED" -> "진행 중"
        "ABANDONED" -> "중단"
        else -> this
    }

private const val QUIZ_SESSION_CARD_ALPHA = 0.45f
private const val ISO_DATE_LENGTH = 10
