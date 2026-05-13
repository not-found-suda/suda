package com.ssafy.mobile.feature.conversation.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.MessageStatus
import com.ssafy.mobile.feature.conversation.domain.model.SenderType

private const val PENDING_ALPHA = 0.6f
private const val MAX_BUBBLE_WIDTH = 280

// 후속 작업: 번역 신고/피드백 백엔드 미구현 상태라 임시 숨김 처리 (백엔드 연동 시 true로 변경)
private const val IS_TRANSLATION_FEEDBACK_ENABLED = false

@Composable
fun SubtitleBubble(
    message: ChatMessage,
    onFeedbackClick: ((ChatMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val style = subtitleBubbleStyle(message.senderType)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        contentAlignment = style.boxAlignment,
    ) {
        Column(
            modifier = Modifier.animateContentSize(),
            horizontalAlignment = style.columnAlignment,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBadge(
                    text = message.senderType.senderLabel(),
                    tone = message.senderType.senderBadgeTone(),
                )
                if (message.status == MessageStatus.PENDING) {
                    AppBadge(
                        text = "처리 중",
                        tone = AppBadgeTone.Warning,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .widthIn(max = MAX_BUBBLE_WIDTH.dp)
                        .background(style.backgroundColor, style.shape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .alpha(if (message.status == MessageStatus.PENDING) PENDING_ALPHA else 1f),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = style.contentColor,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (IS_TRANSLATION_FEEDBACK_ENABLED &&
                message.canReportTranslation() &&
                onFeedbackClick != null
            ) {
                TextButton(onClick = { onFeedbackClick(message) }) {
                    Text(
                        text = "신고",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun ChatMessage.canReportTranslation(): Boolean =
    isFeedbackAvailable && status == MessageStatus.COMPLETED

private fun SenderType.senderLabel(): String =
    when (this) {
        SenderType.SYSTEM -> "안내"
        SenderType.PARENT -> "부모"
        SenderType.CHILD -> "아이"
    }

private fun SenderType.senderBadgeTone(): AppBadgeTone =
    when (this) {
        SenderType.SYSTEM -> AppBadgeTone.Neutral
        SenderType.PARENT -> AppBadgeTone.Primary
        SenderType.CHILD -> AppBadgeTone.Secondary
    }

@Composable
private fun subtitleBubbleStyle(senderType: SenderType): SubtitleBubbleStyle =
    when (senderType) {
        SenderType.SYSTEM ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.Center,
                columnAlignment = Alignment.CenterHorizontally,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
        SenderType.PARENT ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.CenterStart,
                columnAlignment = Alignment.Start,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp),
            )
        SenderType.CHILD ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.CenterEnd,
                columnAlignment = Alignment.End,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(8.dp, 2.dp, 8.dp, 8.dp),
            )
    }

private data class SubtitleBubbleStyle(
    val boxAlignment: Alignment,
    val columnAlignment: Alignment.Horizontal,
    val backgroundColor: Color,
    val contentColor: Color,
    val shape: Shape,
)
