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
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.MessageStatus
import com.ssafy.mobile.feature.conversation.domain.model.SenderType

private const val PENDING_ALPHA = 0.6f
private const val MAX_BUBBLE_WIDTH = 300
private const val IS_TRANSLATION_FEEDBACK_ENABLED = false
private const val SYSTEM_BUBBLE_ALPHA = 0.52f

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
            if (message.senderType != SenderType.SYSTEM) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message.senderType.senderLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = style.metaColor,
                        fontWeight = FontWeight.Bold,
                    )
                    if (message.status == MessageStatus.PENDING) {
                        Text(
                            text = "처리 중",
                            style = MaterialTheme.typography.labelSmall,
                            color = style.metaColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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
                        color = style.metaColor,
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
        SenderType.PARENT -> "보호자"
        SenderType.CHILD -> "아이"
    }

@Composable
private fun subtitleBubbleStyle(senderType: SenderType): SubtitleBubbleStyle =
    when (senderType) {
        SenderType.SYSTEM ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.Center,
                columnAlignment = Alignment.CenterHorizontally,
                backgroundColor =
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SYSTEM_BUBBLE_ALPHA),
                contentColor = Color.White,
                metaColor = Color.White.copy(alpha = 0.84f),
                shape = RoundedCornerShape(8.dp),
            )
        SenderType.PARENT ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.CenterEnd,
                columnAlignment = Alignment.End,
                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.52f),
                contentColor = Color.White,
                metaColor = Color.White.copy(alpha = 0.84f),
                shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
            )
        SenderType.CHILD ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.CenterStart,
                columnAlignment = Alignment.Start,
                backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.48f),
                contentColor = Color.White,
                metaColor = Color.White.copy(alpha = 0.84f),
                shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
            )
    }

private data class SubtitleBubbleStyle(
    val boxAlignment: Alignment,
    val columnAlignment: Alignment.Horizontal,
    val backgroundColor: Color,
    val contentColor: Color,
    val metaColor: Color,
    val shape: Shape,
)
