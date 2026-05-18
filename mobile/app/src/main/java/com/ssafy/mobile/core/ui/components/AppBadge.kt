@file:Suppress("MagicNumber")

package com.ssafy.mobile.core.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.theme.SudaInfo
import com.ssafy.mobile.core.ui.theme.SudaSuccess

@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: AppBadgeTone = AppBadgeTone.Neutral,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
) {
    val colors = tone.colors()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

enum class AppBadgeTone {
    Primary,
    Secondary,
    Tertiary,
    Success,
    Warning,
    Error,
    Neutral,
}

@Composable
private fun AppBadgeTone.colors(): AppBadgeColors =
    when (this) {
        AppBadgeTone.Primary ->
            AppBadgeColors(
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        AppBadgeTone.Secondary ->
            AppBadgeColors(
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        AppBadgeTone.Tertiary ->
            AppBadgeColors(
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        AppBadgeTone.Success ->
            AppBadgeColors(
                container = SudaSuccess.copy(alpha = 0.14f),
                content = SudaSuccess,
            )
        AppBadgeTone.Warning ->
            AppBadgeColors(
                container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        AppBadgeTone.Error ->
            AppBadgeColors(
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        AppBadgeTone.Neutral ->
            AppBadgeColors(
                container = SudaInfo.copy(alpha = 0.12f),
                content = SudaInfo,
            )
    }

private data class AppBadgeColors(
    val container: Color,
    val content: Color,
)
