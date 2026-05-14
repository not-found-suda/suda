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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgress

@Composable
fun ReportCategoryProgressCard(category: ReportCategoryProgress) {
    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            ReportCategoryProgressHeader(category = category)
            Spacer(modifier = Modifier.height(14.dp))
            ReportCategoryMetricRow(category = category)
            Spacer(modifier = Modifier.height(14.dp))
            ReportRateProgress(
                title = "출제 단어 비율",
                value = category.quizCoverageRate,
                detail = "${category.quizzedWordCount}/${category.totalWordCount}개",
                tone = ReportVisualTone.Primary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ReportRateProgress(
                title = "정답 경험 단어 비율",
                value = category.correctWordRate,
                detail = "${category.correctWordCount}/${category.totalWordCount}개",
                tone = ReportVisualTone.Success,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ReportRateProgress(
                title = "문제 정답률",
                value = category.accuracyRate,
                detail = "${category.correctCount}/${category.totalQuestionCount}문제",
                tone = ReportVisualTone.Tertiary,
            )
        }
    }
}

@Composable
private fun ReportCategoryProgressHeader(category: ReportCategoryProgress) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = category.categoryName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AppBadge(
            text = "최근 퀴즈 ${category.latestSessionAt.toReportDateLabel()}",
            tone = AppBadgeTone.Neutral,
        )
    }
}

@Composable
private fun ReportCategoryMetricRow(category: ReportCategoryProgress) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReportCategoryMetric(
            title = "완료 퀴즈",
            value = "${category.completedSessionCount}회",
            tone = ReportVisualTone.Primary,
            modifier = Modifier.weight(1f),
        )
        ReportCategoryMetric(
            title = "문제 수",
            value = "${category.totalQuestionCount}",
            tone = ReportVisualTone.Tertiary,
            modifier = Modifier.weight(1f),
        )
        ReportStarMetricTile(
            title = "평균 별점",
            rating = category.averageStar,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReportCategoryMetric(
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

@Composable
private fun ReportRateProgress(
    title: String,
    value: Double,
    detail: String,
    tone: ReportVisualTone,
) {
    ReportPercentMeter(
        title = title,
        value = value,
        detail = detail,
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
