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
    val isParent = message.senderType == SenderType.PARENT
    val alignment = if (isParent) Alignment.CenterStart else Alignment.CenterEnd
    val backgroundColor =
        if (isParent) {
            Color(SUDA_BG_COLOR)
        } else {
            MaterialTheme.colorScheme.primary
        }
    val contentColor = if (isParent) Color.Black else Color.White
    val shape =
        if (isParent) {
            RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
        } else {
            RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        contentAlignment = alignment,
    ) {
        Column(
            horizontalAlignment = if (isParent) Alignment.Start else Alignment.End,
        ) {
            Box(
                modifier =
                    Modifier
                        .widthIn(max = MAX_BUBBLE_WIDTH.dp)
                        .background(backgroundColor, shape)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .alpha(if (message.status == MessageStatus.PENDING) PENDING_ALPHA else 1f),
            ) {
                Text(
                    text = message.text,
                    color = contentColor,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}
