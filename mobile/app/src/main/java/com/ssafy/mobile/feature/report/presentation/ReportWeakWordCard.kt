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
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWord

@Composable
fun ReportWeakWordCard(word: ReportWeakWord) {
    val correctCount = (word.attemptCount - word.wrongCount).coerceAtLeast(0)

    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val wordLabel = word.displayText?.takeIf { it.isNotBlank() } ?: word.word
                    Text(
                        text = wordLabel,
                        style = MaterialTheme.typography.titleMedium,
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
                AppBadge(
                    text = "${word.wrongCount}회 틀림",
                    tone = AppBadgeTone.Error,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            ReportWeakWordMetricRow(word = word)
            Spacer(modifier = Modifier.height(12.dp))
            ReportPercentMeter(
                title = "정답률",
                value = word.accuracyRate,
                detail = "$correctCount/${word.attemptCount}회 정답",
                tone = ReportVisualTone.Warning,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "최근 답변 ${word.lastAnsweredAt.toReportDateLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportWeakWordMetricRow(word: ReportWeakWord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportWeakWordMetric(
            title = "시도",
            value = "${word.attemptCount}회",
            tone = ReportVisualTone.Neutral,
            modifier = Modifier.weight(1f),
        )
        ReportWeakWordMetric(
            title = "오답",
            value = "${word.wrongCount}회",
            tone = ReportVisualTone.Error,
            modifier = Modifier.weight(1f),
        )
        ReportStarMetricTile(
            title = "평균 별점",
            rating = word.averageStar,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReportWeakWordMetric(
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

private fun String?.toReportDateLabel(): String =
    if (isNullOrBlank()) {
        "정보 없음"
    } else {
        take(ISO_DATE_LENGTH).replace("-", ".")
    }

private const val ISO_DATE_LENGTH = 10
