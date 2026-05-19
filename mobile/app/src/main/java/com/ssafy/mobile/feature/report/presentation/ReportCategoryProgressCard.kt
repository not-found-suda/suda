@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgress
import java.util.Locale

@Composable
fun ReportCategoryProgressCard(category: ReportCategoryProgress) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    ReportGlassCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
        ) {
            // 접혀있을 때 보이는 헤더 영역 (카테고리명, 최근 퀴즈일, 토글 버튼)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 카테고리별 귀여운 이모지 박스
                val categoryEmoji = getCategoryEmoji(category.categoryName)
                val categoryIconColor =
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(46.dp)
                            .background(
                                color = categoryIconColor,
                                shape = RoundedCornerShape(14.dp),
                            ),
                ) {
                    Text(
                        text = categoryEmoji,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "최근 퀴즈: ${category.latestSessionAt.toReportDateLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 동글동글한 회전하는 화살표 버튼
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                            ),
                ) {
                    Text(
                        text = "▼",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(rotationState),
                    )
                }
            }

            // 토글 펼쳤을 때 나타나는 상세 영역
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 수치 박스 2개 (완료 퀴즈, 문제 수 - 둘 다 파란색으로 통일!)
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
                            tone = ReportVisualTone.Primary, // 파란색으로 통일!
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 평균 별점 (별 이모지 처리)
                    ReportInlineStarMetric(
                        title = "평균 별점",
                        rating = category.averageStar,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 대망의 원형 그래프 대시보드 UI! (3개 배치)
                    Text(
                        text = "카테고리 학습 상세 분석",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. 출제 비율 원형 게이지
                        CircularProgressGauge(
                            title = "출제 단어",
                            rate = category.quizCoverageRate,
                            detail = "${category.quizzedWordCount}/${category.totalWordCount}개",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )

                        // 2. 정답 경험 원형 게이지
                        CircularProgressGauge(
                            title = "정답 경험",
                            rate = category.correctWordRate,
                            detail = "${category.correctWordCount}/${category.totalWordCount}개",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )

                        // 3. 문제 정답률 원형 게이지
                        CircularProgressGauge(
                            title = "문제 정답률",
                            rate = category.accuracyRate,
                            detail = "${category.correctCount}/${category.totalQuestionCount}문제",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 대시보드 스타일의 귀엽고 정돈된 원형 게이지 컴포넌트
 */
@Composable
private fun CircularProgressGauge(
    title: String,
    rate: Double,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress = (rate / 100.0).toFloat().coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp),
    ) {
        // 원형 그래프 + 내부에 퍼센트 텍스트 배치
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(68.dp),
        ) {
            // 배경 회색 트랙
            CircularProgressIndicator(
                progress = { 1f },
                color = color.copy(alpha = 0.12f),
                strokeWidth = 6.dp,
                modifier = Modifier.fillMaxSize(),
            )
            // 활성 게이지
            CircularProgressIndicator(
                progress = { progress },
                color = color,
                strokeWidth = 6.dp,
                modifier = Modifier.fillMaxSize(),
            )
            // 퍼센트 텍스트
            Text(
                text = String.format(Locale.KOREA, "%.0f%%", rate),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 소제목
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        // 수치 디테일
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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

/**
 * 카테고리 이름에 맞는 알맞고 직관적인 이모지를 매핑해주는 헬퍼
 */
private fun getCategoryEmoji(categoryName: String): String =
    when {
        categoryName.contains(
            "음식",
        ) ||
            categoryName.contains("과일") ||
            categoryName.contains("식") -> "🍎"
        categoryName.contains("동물") || categoryName.contains("곤충") -> "🦁"
        categoryName.contains(
            "가족",
        ) ||
            categoryName.contains("사람") ||
            categoryName.contains("인물") -> "👨‍👩‍👧"
        categoryName.contains("탈것") || categoryName.contains("교통") -> "🚗"
        categoryName.contains("색깔") || categoryName.contains("색상") -> "🎨"
        categoryName.contains("자연") || categoryName.contains("날씨") -> "☀️"
        categoryName.contains("물건") || categoryName.contains("사물") -> "🧸"
        categoryName.contains("신체") || categoryName.contains("몸") -> "🖐️"
        else -> "💡"
    }

private fun String?.toReportDateLabel(): String =
    if (isNullOrBlank()) {
        "정보 없음"
    } else {
        take(ISO_DATE_LENGTH).replace("-", ".")
    }

private const val ISO_DATE_LENGTH = 10
