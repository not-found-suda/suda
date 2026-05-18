@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.theme.SudaInfo
import com.ssafy.mobile.core.ui.theme.SudaSuccess

private const val ENTER_SLIDE_DIVISOR = 5

internal enum class QuizMessageVisual(
    val badgeText: String,
    val symbol: String,
    val badgeTone: AppBadgeTone,
    val isLoading: Boolean = false,
) {
    Loading(
        badgeText = "준비 중",
        symbol = "",
        badgeTone = AppBadgeTone.Primary,
        isLoading = true,
    ),
    Success(
        badgeText = "완료",
        symbol = "★",
        badgeTone = AppBadgeTone.Success,
    ),
    Error(
        badgeText = "문제 발생",
        symbol = "!",
        badgeTone = AppBadgeTone.Error,
    ),
    Empty(
        badgeText = "준비 중",
        symbol = "?",
        badgeTone = AppBadgeTone.Neutral,
    ),
    Default(
        badgeText = "퀴즈",
        symbol = "★",
        badgeTone = AppBadgeTone.Primary,
    ),
}

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
        visual = QuizMessageVisual.Success,
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
    visual: QuizMessageVisual = QuizMessageVisual.Default,
) {
    val accentColor = visual.accentColor()
    val iconContainerColor = visual.iconContainerColor()
    val iconContentColor = visual.iconContentColor()

    Box(
        modifier =
            modifier
                .padding(24.dp)
                .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = title.isNotBlank(),
            enter =
                fadeIn() +
                    scaleIn(initialScale = 0.94f) +
                    slideInVertically { it / ENTER_SLIDE_DIVISOR },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 1.dp,
                border =
                    BorderStroke(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.16f),
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            accentColor.copy(alpha = 0.10f),
                                            Color.Transparent,
                                        ),
                                ),
                            ).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    QuizMessageIcon(
                        visual = visual,
                        containerColor = iconContainerColor,
                        contentColor = iconContentColor,
                    )
                    AppBadge(
                        text = visual.badgeText,
                        tone = visual.badgeTone,
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

@Composable
private fun QuizMessageIcon(
    visual: QuizMessageVisual,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.size(76.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (visual.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 3.dp,
                    color = contentColor,
                )
            } else {
                Text(
                    text = visual.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun QuizMessageVisual.accentColor(): Color =
    when (this) {
        QuizMessageVisual.Loading,
        QuizMessageVisual.Default,
        -> MaterialTheme.colorScheme.primary
        QuizMessageVisual.Success -> SudaSuccess
        QuizMessageVisual.Error -> MaterialTheme.colorScheme.error
        QuizMessageVisual.Empty -> SudaInfo
    }

@Composable
private fun QuizMessageVisual.iconContainerColor(): Color =
    when (this) {
        QuizMessageVisual.Loading,
        QuizMessageVisual.Default,
        -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        QuizMessageVisual.Success -> SudaSuccess.copy(alpha = 0.14f)
        QuizMessageVisual.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
        QuizMessageVisual.Empty -> SudaInfo.copy(alpha = 0.12f)
    }

@Composable
private fun QuizMessageVisual.iconContentColor(): Color =
    when (this) {
        QuizMessageVisual.Loading,
        QuizMessageVisual.Default,
        -> MaterialTheme.colorScheme.primary
        QuizMessageVisual.Success -> SudaSuccess
        QuizMessageVisual.Error -> MaterialTheme.colorScheme.error
        QuizMessageVisual.Empty -> SudaInfo
    }
