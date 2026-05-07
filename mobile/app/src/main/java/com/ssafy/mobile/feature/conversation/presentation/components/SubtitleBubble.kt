package com.ssafy.mobile.feature.conversation.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.MessageStatus
import com.ssafy.mobile.feature.conversation.domain.model.SenderType

private const val SUDA_BG_COLOR = 0xFFF0F0F0
private const val PENDING_ALPHA = 0.6f
private const val MAX_BUBBLE_WIDTH = 280

@Composable
fun SubtitleBubble(
    message: ChatMessage,
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
            horizontalAlignment = style.columnAlignment,
        ) {
            Box(
                modifier =
                    Modifier
                        .widthIn(max = MAX_BUBBLE_WIDTH.dp)
                        .background(style.backgroundColor, style.shape)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .alpha(if (message.status == MessageStatus.PENDING) PENDING_ALPHA else 1f),
            ) {
                Text(
                    text = message.text,
                    color = style.contentColor,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun subtitleBubbleStyle(senderType: SenderType): SubtitleBubbleStyle =
    when (senderType) {
        SenderType.SYSTEM ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.Center,
                columnAlignment = Alignment.CenterHorizontally,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(16.dp),
            )
        SenderType.PARENT ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.CenterStart,
                columnAlignment = Alignment.Start,
                backgroundColor = Color(SUDA_BG_COLOR),
                contentColor = Color.Black,
                shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            )
        SenderType.CHILD ->
            SubtitleBubbleStyle(
                boxAlignment = Alignment.CenterEnd,
                columnAlignment = Alignment.End,
                backgroundColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            )
    }

private data class SubtitleBubbleStyle(
    val boxAlignment: Alignment,
    val columnAlignment: Alignment.Horizontal,
    val backgroundColor: Color,
    val contentColor: Color,
    val shape: Shape,
)
