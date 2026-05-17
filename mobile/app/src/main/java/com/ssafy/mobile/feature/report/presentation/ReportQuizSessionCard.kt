package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
                tone = ReportVisualTone.Success,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "평균 별점",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(8.dp))
                ReportStarRating(rating = session.averageStar)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = session.toDateLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun ReportQuizSession.toDateLabel(): String =
    when {
        !endedAt.isNullOrBlank() -> "완료 ${endedAt.toReportQuizSessionDateLabel()}"
        !startedAt.isNullOrBlank() -> "시작 ${startedAt.toReportQuizSessionDateLabel()}"
        else -> "날짜 정보 없음"
    }
