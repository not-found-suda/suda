package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgress
import java.util.Locale

@Composable
fun ReportCategoryProgressCard(category: ReportCategoryProgress) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CATEGORY_CARD_ALPHA),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            ReportCategoryProgressHeader(category = category)
            Spacer(modifier = Modifier.height(14.dp))
            ReportCategoryMetricRow(category = category)
            Spacer(modifier = Modifier.height(14.dp))
            ReportRateProgress(
                title = "출제 단어 비율",
                value = category.quizCoverageRate,
                detail = "${category.quizzedWordCount}/${category.totalWordCount}개",
            )
            Spacer(modifier = Modifier.height(10.dp))
            ReportRateProgress(
                title = "정답 경험 단어 비율",
                value = category.correctWordRate,
                detail = "${category.correctWordCount}/${category.totalWordCount}개",
            )
            Spacer(modifier = Modifier.height(10.dp))
            ReportRateProgress(
                title = "문제 정답률",
                value = category.accuracyRate,
                detail = "${category.correctCount}/${category.totalQuestionCount}문제",
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
        Text(
            text = "최근 퀴즈 ${category.latestSessionAt.toReportDateLabel()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.weight(1f),
        )
        ReportCategoryMetric(
            title = "문제 수",
            value = "${category.totalQuestionCount}",
            modifier = Modifier.weight(1f),
        )
        ReportCategoryMetric(
            title = "평균 별점",
            value = String.format(Locale.KOREA, "%.1f/3", category.averageStar),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReportCategoryMetric(
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
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReportRateProgress(
    title: String,
    value: Double,
    detail: String,
) {
    val safeValue = value.coerceIn(PERCENT_MIN, PERCENT_MAX)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${String.format(Locale.KOREA, "%.1f", safeValue)}% · $detail",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (safeValue / PERCENT_MAX).toFloat() },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(PROGRESS_CORNER_RADIUS.dp)),
            trackColor = MaterialTheme.colorScheme.surface,
        )
    }
}

private fun String?.toReportDateLabel(): String =
    if (isNullOrBlank()) {
        "정보 없음"
    } else {
        take(ISO_DATE_LENGTH).replace("-", ".")
    }

private const val CATEGORY_CARD_ALPHA = 0.45f
private const val ISO_DATE_LENGTH = 10
private const val PERCENT_MIN = 0.0
private const val PERCENT_MAX = 100.0
private const val PROGRESS_CORNER_RADIUS = 999
