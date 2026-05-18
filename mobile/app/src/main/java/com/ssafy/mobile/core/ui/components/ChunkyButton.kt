@file:Suppress("LongMethod", "MagicNumber", "MatchingDeclarationName")

package com.ssafy.mobile.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChunkyButtonTone {
    Primary,
    Secondary,
    Success,
    Warning,
    Danger,
}

@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ChunkyButtonTone = ChunkyButtonTone.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 색상 테마 설정
    val (topColor, bottomColor, textColor) =
        when (tone) {
            ChunkyButtonTone.Primary ->
                Triple(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), // 임시 섀도우 색상
                    MaterialTheme.colorScheme.onPrimary,
                )
            ChunkyButtonTone.Secondary ->
                Triple(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            ChunkyButtonTone.Success ->
                Triple(
                    Color(0xFF58CC02), // 듀오링고 그린
                    Color(0xFF58A700),
                    Color.White,
                )
            ChunkyButtonTone.Warning ->
                Triple(
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f),
                    MaterialTheme.colorScheme.onSecondary,
                )
            ChunkyButtonTone.Danger ->
                Triple(
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    MaterialTheme.colorScheme.onError,
                )
        }

    val actualTopColor = if (enabled) topColor else MaterialTheme.colorScheme.surfaceVariant
    val actualBottomColor =
        if (enabled) {
            bottomColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
                .copy(
                    alpha = 0.2f,
                )
        }
    val actualTextColor = if (enabled) textColor else MaterialTheme.colorScheme.onSurfaceVariant

    // 눌렸을 때 Y축으로 내려가는 애니메이션
    val offsetY by animateDpAsState(
        targetValue = if (isPressed && enabled && !loading) 4.dp else 0.dp,
        label = "buttonPress",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(60.dp) // 버튼 높이 지정
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null, // 기본 리플 효과 제거
                    enabled = enabled && !loading,
                    onClick = onClick,
                ),
    ) {
        // 배경 그림자 (항상 고정)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .offset(y = 4.dp)
                    .background(actualBottomColor, RoundedCornerShape(16.dp)),
        )
        // 실제 버튼 (눌리면 아래로 내려감)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .offset(y = offsetY)
                    .background(actualTopColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = actualTextColor,
                )
            } else {
                Text(
                    text = text.uppercase(),
                    color = actualTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
