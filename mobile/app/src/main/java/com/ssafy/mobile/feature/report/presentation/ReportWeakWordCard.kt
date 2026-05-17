package com.ssafy.mobile.feature.report.presentation

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
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWord

@Composable
fun ReportWeakWordCard(
    word: ReportWeakWord,
    onLearnClick: () -> Unit,
) {
    val correctCount = (word.attemptCount - word.wrongCount).coerceAtLeast(0)

    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onLearnClick,
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
                Text(
                    text = "학습하기",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            ReportPercentMeter(
                title = "정답률 ($correctCount/${word.attemptCount})",
                value = word.accuracyRate,
                tone = ReportVisualTone.Warning,
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
                ReportStarRating(rating = word.averageStar)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "최근 ${word.lastAnsweredAt.toReportDateLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun String?.toReportDateLabel(): String =
    if (isNullOrBlank()) {
        "정보 없음"
    } else {
        take(ISO_DATE_LENGTH).replace("-", ".")
    }

private const val ISO_DATE_LENGTH = 10
