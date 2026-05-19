@file:Suppress("CyclomaticComplexMethod", "MagicNumber", "MaxLineLength", "TooManyFunctions")

package com.ssafy.mobile.feature.report.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.theme.SudaHomeAccent
import com.ssafy.mobile.core.ui.theme.SudaInfo
import com.ssafy.mobile.core.ui.theme.SudaReportAccent
import com.ssafy.mobile.core.ui.theme.SudaSuccess
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun ReportGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val cardModifier =
        modifier
            .border(
                border =
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.54f),
                    ),
                shape = shape,
            )

    if (onClick == null) {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}

@Composable
internal fun ReportSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ReportSectionMarker(title = title)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReportSectionMarker(title: String) {
    val (iconText, bgColor) =
        when (title) {
            "기록" -> "📝" to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f)
            "이번 기간 요약" -> "📊" to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
            else -> "✨" to MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
        }

    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = iconText,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
internal fun ReportMetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: ReportVisualTone = ReportVisualTone.Primary,
) {
    val colors = tone.reportVisualColors()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.container,
        contentColor = colors.content,
        border =
            BorderStroke(
                width = 1.dp,
                color = colors.accent.copy(alpha = 0.22f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.content.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.content.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ReportStarMetricTile(
    title: String,
    rating: Double?,
    modifier: Modifier = Modifier,
    emptyText: String = "정보 없음",
) {
    val colors = ReportVisualTone.Warning.reportVisualColors()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.container,
        contentColor = colors.content,
        border =
            BorderStroke(
                width = 1.dp,
                color = colors.accent.copy(alpha = 0.22f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.content.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rating == null) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                ReportStarRating(rating = rating)
            }
        }
    }
}

@Composable
internal fun ReportStarRating(
    rating: Double,
    modifier: Modifier = Modifier,
    showValue: Boolean = true,
) {
    val filledCount = rating.roundToInt().coerceIn(STAR_MIN, STAR_MAX)
    val safeRating =
        rating.coerceIn(
            STAR_MIN.toDouble(),
            STAR_MAX.toDouble(),
        )
    val label = String.format(Locale.KOREA, "%.1f", safeRating)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(STAR_MAX) { index ->
            Text(
                text = "⭐",
                style = MaterialTheme.typography.headlineSmall,
                modifier = if (index < filledCount) Modifier else Modifier.alpha(0.3f),
            )
        }
        if (showValue) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label/3",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ReportInlineStarMetric(
    title: String,
    rating: Double?,
    modifier: Modifier = Modifier,
    emptyText: String = "정보 없음",
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (rating == null) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            ReportStarRating(rating = rating)
        }
    }
}

@Composable
internal fun ReportPercentMeter(
    title: String,
    value: Double,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: ReportVisualTone = ReportVisualTone.Primary,
) {
    val safeValue = value.coerceIn(PERCENT_MIN, PERCENT_MAX)
    val progress = (safeValue / PERCENT_MAX).toFloat()
    val colors = tone.reportVisualColors()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = safeValue.toPercentLabel(detail),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.progressBrush),
            )
        }
    }
}

internal enum class ReportVisualTone {
    Primary,
    Secondary,
    Tertiary,
    Success,
    Warning,
    Error,
    Neutral,
}

@Composable
private fun ReportVisualTone.reportVisualColors(): ReportVisualColors {
    val primary = SudaHomeAccent
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = SudaReportAccent
    val error = MaterialTheme.colorScheme.error

    return when (this) {
        ReportVisualTone.Primary ->
            ReportVisualColors(
                container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                accent = primary,
                progressBrush = primary.toReportProgressBrush(),
            )
        ReportVisualTone.Secondary ->
            ReportVisualColors(
                container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                accent = secondary,
                progressBrush = secondary.toReportProgressBrush(),
            )
        ReportVisualTone.Tertiary ->
            ReportVisualColors(
                container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f),
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                accent = tertiary,
                progressBrush = tertiary.toReportProgressBrush(),
            )
        ReportVisualTone.Success ->
            ReportVisualColors(
                container = SudaSuccess.copy(alpha = 0.11f),
                content = SudaSuccess,
                accent = SudaSuccess,
                progressBrush = SudaSuccess.toReportProgressBrush(),
            )
        ReportVisualTone.Warning ->
            ReportVisualColors(
                container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f),
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                accent = secondary,
                progressBrush = secondary.toReportProgressBrush(startAlpha = 0.62f),
            )
        ReportVisualTone.Error ->
            ReportVisualColors(
                container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.54f),
                content = MaterialTheme.colorScheme.onErrorContainer,
                accent = error,
                progressBrush = error.toReportProgressBrush(startAlpha = 0.62f),
            )
        ReportVisualTone.Neutral ->
            ReportVisualColors(
                container = SudaInfo.copy(alpha = 0.1f),
                content = SudaInfo,
                accent = SudaInfo,
                progressBrush = SudaInfo.toReportProgressBrush(startAlpha = 0.68f),
            )
    }
}

private fun Color.toReportProgressBrush(startAlpha: Float = 0.72f): Brush =
    Brush.horizontalGradient(
        colors =
            listOf(
                copy(alpha = startAlpha),
                this,
            ),
    )

private data class ReportVisualColors(
    val container: Color,
    val content: Color,
    val accent: Color,
    val progressBrush: Brush,
)

private fun Double.toPercentLabel(detail: String?): String {
    val percent = String.format(Locale.KOREA, "%.1f%%", this)
    return if (detail.isNullOrBlank()) percent else "$percent · $detail"
}

private const val STAR_MIN = 0
private const val STAR_MAX = 3
private const val PERCENT_MIN = 0.0
private const val PERCENT_MAX = 100.0

internal fun getWordEmoji(
    word: String,
    categoryName: String,
): String {
    val cleanWord = word.trim()
    return when {
        // --- 1. 동물/새 ---
        cleanWord.contains("강아지") -> "🐶"
        cleanWord.contains("기린") -> "🦒"
        cleanWord.contains("곰") -> "🐻"
        cleanWord.contains("소") && !cleanWord.contains("소방차") && !cleanWord.contains("코뿔소") -> "🐮"
        cleanWord.contains("말") -> "🐴"
        cleanWord.contains("새") && !cleanWord.contains("우주선") -> "🐦"
        cleanWord.contains("토끼") -> "🐰"
        cleanWord.contains("사자") -> "🦁"
        cleanWord.contains("악어") -> "🐊"
        cleanWord.contains("오리") -> "🦆"
        cleanWord.contains("하마") -> "🦛"
        cleanWord.contains("코뿔소") -> "🦏"
        cleanWord.contains("원숭이") -> "🐵"
        cleanWord.contains("다람쥐") -> "🐿️"
        cleanWord.contains("캥거루") -> "🦘"

        // --- 2. 음식 ---
        cleanWord.contains("사과") -> "🍎"
        cleanWord.contains("우유") -> "🥛"
        cleanWord.contains("밥") -> "🍚"
        cleanWord.contains("빵") -> "🍞"
        cleanWord.contains("딸기") -> "🍓"
        cleanWord.contains("옥수수") -> "🌽"
        cleanWord.contains("요구르트") -> "🧃"
        cleanWord.contains("샌드위치") -> "🥪"
        cleanWord.contains("스파게티") -> "🍝"
        cleanWord.contains("브로콜리") -> "🥦"
        cleanWord.contains("당근") -> "🥕"
        cleanWord.contains("치즈") -> "🧀"
        cleanWord.contains("계란") -> "🥚"
        cleanWord.contains("고기") -> "🥩"
        cleanWord.contains("포도") -> "🍇"

        // --- 3. 가족/인물 ---
        cleanWord.contains("할아버지") -> "👴"
        cleanWord.contains("할머니") -> "👵"
        cleanWord.contains("나") && (cleanWord.length == 1 || cleanWord == "나") -> "👶"
        cleanWord.contains("엄마") -> "👩"
        cleanWord.contains("아빠") -> "👨"

        // --- 4. 색깔 ---
        cleanWord.contains("검정") -> "⚫"
        cleanWord.contains("하양") -> "⚪"
        cleanWord.contains("하늘색") -> "🩵"
        cleanWord.contains("남색") -> "🔵"
        cleanWord.contains("파랑") -> "🔵"
        cleanWord.contains("노랑") -> "🟡"
        cleanWord.contains("빨강") -> "🔴"
        cleanWord.contains("금색") -> "✨"
        cleanWord.contains("연두색") -> "🌱"
        cleanWord.contains("무지개색") -> "🌈"
        cleanWord.contains("갈색") -> "🤎"
        cleanWord.contains("회색") -> "🩶"
        cleanWord.contains("주황") -> "🟠"
        cleanWord.contains("초록") -> "🟢"
        cleanWord.contains("보라") -> "🟣"

        // --- 5. 탈것 ---
        cleanWord.contains("경찰차") -> "🚓"
        cleanWord.contains("구급차") -> "🚑"
        cleanWord.contains("소방차") -> "🚒"
        cleanWord.contains("자동차") -> "🚗"
        cleanWord.contains("택시") -> "🚕"
        cleanWord.contains("버스") -> "🚌"
        cleanWord.contains("기차") -> "🚂"
        cleanWord.contains("배") &&
            (cleanWord == "배" || categoryName.contains("탈것") || categoryName.contains("교통")) -> "🚢"
        cleanWord.contains("포크레인") -> "🚜"
        cleanWord.contains("우주선") -> "🚀"
        cleanWord.contains("잠수함") -> "⚓"
        cleanWord.contains("헬리콥터") -> "🚁"
        cleanWord.contains("오토바이") -> "🏍️"
        cleanWord.contains("비행기") -> "✈️"
        cleanWord.contains("자전거") -> "🚲"

        // --- 6. 카테고리 폴백 ---
        categoryName.contains("음식") || categoryName.contains("과일") -> "🍽️"
        categoryName.contains("동물") || categoryName.contains("곤충") -> "🐾"
        categoryName.contains("가족") || categoryName.contains("사람") -> "👨‍👩‍👧"
        categoryName.contains("탈것") || categoryName.contains("교통") -> "🚗"
        categoryName.contains("색깔") || categoryName.contains("색상") -> "🎨"
        else -> "💡"
    }
}
