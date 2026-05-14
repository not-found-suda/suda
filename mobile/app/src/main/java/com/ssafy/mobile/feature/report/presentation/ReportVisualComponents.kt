@file:Suppress("MagicNumber")

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.theme.SudaInfo
import com.ssafy.mobile.core.ui.theme.SudaSuccess
import com.ssafy.mobile.core.ui.theme.SudaWarning
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
            .clip(shape)
            .background(reportGlassBrush(), shape)
            .border(
                border =
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
                    ),
                shape = shape,
            )

    if (onClick == null) {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
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
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
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
    Column(modifier = modifier.fillMaxWidth()) {
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(STAR_MAX) { index ->
            Text(
                text = if (index < filledCount) "★" else "☆",
                style = MaterialTheme.typography.titleSmall,
                color =
                    if (index < filledCount) {
                        SudaWarning
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
            )
        }
        if (showValue) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$label/3",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
private fun reportGlassBrush(): Brush =
    Brush.linearGradient(
        colors =
            listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.18f),
            ),
    )

@Composable
private fun ReportVisualTone.reportVisualColors(): ReportVisualColors {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
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
                container = SudaWarning.copy(alpha = 0.13f),
                content = Color(0xFF6B4200),
                accent = SudaWarning,
                progressBrush = SudaWarning.toReportProgressBrush(startAlpha = 0.7f),
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
