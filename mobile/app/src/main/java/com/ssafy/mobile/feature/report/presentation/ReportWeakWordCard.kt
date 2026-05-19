@file:Suppress("LongMethod")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val wordLabel = word.displayText?.takeIf { it.isNotBlank() } ?: word.word

    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onLearnClick,
    ) {
        Column {
            val wordIconColor =
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 단어별 귀여운 이모지 동그라미 아바타
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(46.dp)
                            .background(
                                color = wordIconColor,
                                shape = RoundedCornerShape(14.dp),
                            ),
                ) {
                    Text(
                        text = getWordEmoji(wordLabel, word.categoryName),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wordLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
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

                // 귀여운 학습하기 알약 버튼
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { onLearnClick() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "학습하기 ✏️",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            ReportPercentMeter(
                title = "정답률 ($correctCount/${word.attemptCount})",
                value = word.accuracyRate,
                tone = ReportVisualTone.Primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                    text = "📅 최근 ${word.lastAnsweredAt.toReportDateLabel()}",
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
