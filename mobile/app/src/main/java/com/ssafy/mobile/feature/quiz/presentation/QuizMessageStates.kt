package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppPrimaryButton

private const val ENTER_SLIDE_DIVISOR = 5

@Composable
internal fun QuizFinishedState(
    solvedCount: Int,
    totalCount: Int,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuizMessageState(
        title = "퀴즈를 모두 풀었어요",
        description = "$totalCount 문제 중 $solvedCount 문제를 답변했어요.",
        actionText = "다시 시작",
        onActionClick = onRestartClick,
        modifier = modifier,
    )
}

@Composable
internal fun QuizMessageState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = title.isNotBlank(),
            enter = fadeIn() + slideInVertically { it / ENTER_SLIDE_DIVISOR },
        ) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppBadge(
                        text = "퀴즈",
                        tone = AppBadgeTone.Primary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    if (actionText != null && onActionClick != null) {
                        AppPrimaryButton(
                            text = actionText,
                            onClick = onActionClick,
                        )
                    }
                }
            }
        }
    }
}
