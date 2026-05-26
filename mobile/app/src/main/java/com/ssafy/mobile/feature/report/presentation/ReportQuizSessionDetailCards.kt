@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.feature.report.domain.model.ReportQuizAnswer
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionDetail

@Composable
internal fun ReportQuizSessionSummaryCard(detail: ReportQuizSessionDetail) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                text = "${detail.categoryName} 퀴즈",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val diffColors = detail.difficulty.toReportDifficultyBadgeColors()
                AppBadge(
                    text = detail.difficulty.toReportDifficultyLabel(),
                    containerColor = diffColors.containerColor,
                    contentColor = diffColors.contentColor,
                )
                AppBadge(
                    text = detail.status.toReportSessionStatusLabel(),
                    tone = detail.status.toReportSessionStatusBadgeTone(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            ReportPercentMeter(
                title = "정답률",
                value = detail.accuracyRate,
                detail = "${detail.correctCount}/${detail.totalQuestionCount}문제",
                tone = ReportVisualTone.Success,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReportQuizSessionSummaryMetricGrid(detail = detail)
            Spacer(modifier = Modifier.height(14.dp))
            ReportQuizSessionDateSection(detail = detail)
        }
    }
}

@Composable
private fun ReportQuizSessionSummaryMetricGrid(detail: ReportQuizSessionDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportQuizSessionDetailMetric(
                title = "정답",
                value = "${detail.correctCount}/${detail.totalQuestionCount}",
                tone = ReportVisualTone.Success,
                modifier = Modifier.weight(1f),
            )
            ReportQuizSessionDetailMetric(
                title = "총 별점",
                value = detail.totalStar?.let { "${it}점" } ?: "정보 없음",
                tone = ReportVisualTone.Warning,
                modifier = Modifier.weight(1f),
            )
        }
        ReportInlineStarMetric(
            title = "평균 별점",
            rating = detail.averageStar,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportQuizSessionDateSection(detail: ReportQuizSessionDetail) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⏱️",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "시작: ${detail.startedAt.toReportQuizSessionDateTimeLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "완료: ${detail.endedAt.toReportQuizSessionDateTimeLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ReportQuizAnswerCard(answer: ReportQuizAnswer) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppBadge(
                        text = "${answer.questionNumber}번 문제",
                        tone = AppBadgeTone.Neutral,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = answer.targetText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppBadge(
                    text = answer.correctnessLabel(),
                    tone = answer.toReportCorrectnessBadgeTone(),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            ReportQuizAnswerMetricRow(answer = answer)

            Spacer(modifier = Modifier.height(14.dp))
            ReportAnswerDetailBox(
                icon = "🎙️",
                title = "인식 결과",
                value = answer.recognizedText.toDisplayText("인식 결과가 없어요."),
                backgroundColor = Color(0xFFE8F5E9).copy(alpha = 0.8f),
            )

            Spacer(modifier = Modifier.height(8.dp))
            ReportAnswerDetailBox(
                icon = "✨",
                title = "피드백",
                value = answer.feedback.toDisplayText("피드백이 없어요."),
                backgroundColor = Color(0xFFE8F5E9).copy(alpha = 0.8f),
            )

            Spacer(modifier = Modifier.height(8.dp))
            ReportAnswerDetailBox(
                icon = "📅",
                title = "답변 시각",
                value = answer.answeredAt.toReportQuizSessionDateTimeLabel(),
                backgroundColor = Color(0xFFE8F5E9).copy(alpha = 0.8f),
                valueStyle = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReportAnswerDetailBox(
    icon: String,
    title: String,
    value: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ReportQuizAnswerMetricRow(answer: ReportQuizAnswer) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportQuizSessionDetailMetric(
            title = "정답 여부",
            value = answer.correctnessLabel(),
            tone = answer.toReportCorrectnessVisualTone(),
            modifier = Modifier.fillMaxWidth(),
        )
        ReportInlineStarMetric(
            title = "별점",
            rating = answer.star?.toDouble(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReportQuizSessionDetailMetric(
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

private fun ReportQuizAnswer.correctnessLabel(): String =
    when (isCorrect) {
        true -> "정답"
        false -> "오답"
        null -> "미채점"
    }

private fun ReportQuizAnswer.toReportCorrectnessVisualTone(): ReportVisualTone =
    when (isCorrect) {
        true -> ReportVisualTone.Success
        false -> ReportVisualTone.Error
        null -> ReportVisualTone.Neutral
    }

private fun String?.toDisplayText(fallback: String): String =
    if (isNullOrBlank()) fallback else this
