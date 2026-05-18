@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.R
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.SudaMascot
import com.ssafy.mobile.core.ui.components.SudaMascotImage
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer

@Composable
internal fun QuizStarResultCard(
    answer: QuizAnswer,
    remainingRetryCount: Int,
    modifier: Modifier = Modifier,
) {
    QuizFeedbackEffects(
        eventKey = answer.feedbackEventKey(),
        cue = answer.toFeedbackCue(),
    )

    AppCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        val eventKey = answer.feedbackEventKey()

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StarRewardBurst(
                eventKey = eventKey,
                isCorrect = answer.isCorrect == true,
                star = answer.star,
            )
            QuizFeedbackMascotMessage(
                mascot = answer.toFeedbackMascot(remainingRetryCount),
                title = answer.toRewardTitle(remainingRetryCount),
                description = answer.toRewardDescription(remainingRetryCount),
            )
        }
    }
}

@Composable
private fun QuizFeedbackMascotMessage(
    mascot: SudaMascot,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SudaMascotImage(
            mascot = mascot,
            contentDescription = null,
            modifier = Modifier.size(70.dp),
        )
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Start,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun StarScoreIndicator(
    star: Int?,
    modifier: Modifier = Modifier,
) {
    val starCount = star?.coerceIn(MIN_STAR, MAX_STAR) ?: MIN_STAR

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(MAX_STAR) { index ->
            Image(
                painter = painterResource(R.drawable.star_reward_small),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(28.dp)
                        .alpha(if (index < starCount) 1f else EMPTY_STAR_ALPHA),
            )
        }
        AppBadge(
            text = star.toStarBadgeText(),
            tone = star.toStarBadgeTone(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun StarRewardBurst(
    eventKey: String?,
    isCorrect: Boolean,
    star: Int?,
    modifier: Modifier = Modifier,
) {
    val scale = remember(eventKey) { Animatable(0.7f) }
    val rotation = remember(eventKey) { Animatable(-10f) }
    val infiniteTransition = rememberInfiniteTransition(label = "starRewardTwinkle")
    val sparkleScale =
        infiniteTransition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.12f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 760, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "sparkleScale",
        )
    val sparkleAlpha =
        infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 620, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "sparkleAlpha",
        )

    LaunchedEffect(eventKey) {
        if (eventKey == null) return@LaunchedEffect

        scale.snapTo(0.7f)
        rotation.snapTo(-10f)
        scale.animateTo(
            targetValue = 1.18f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )
        rotation.animateTo(
            targetValue = 4f,
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
        )
        rotation.animateTo(
            targetValue = 0f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
        )
    }

    StarRewardBurstContent(
        isCorrect = isCorrect,
        star = star,
        starScale = scale.value,
        starRotation = rotation.value,
        sparkleScale = sparkleScale.value,
        sparkleAlpha = sparkleAlpha.value,
        modifier = modifier,
    )
}

@Composable
private fun StarRewardBurstContent(
    isCorrect: Boolean,
    star: Int?,
    starScale: Float,
    starRotation: Float,
    sparkleScale: Float,
    sparkleAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .size(184.dp),
        contentAlignment = Alignment.Center,
    ) {
        RewardSparkle(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 30.dp, y = 12.dp),
            size = 44,
            scale = sparkleScale,
            alpha = sparkleAlpha,
        )
        RewardSparkle(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-24).dp, y = (-8).dp),
            size = 38,
            scale = sparkleScale * 0.92f,
            alpha = sparkleAlpha,
        )
        Image(
            painter =
                painterResource(
                    if (isCorrect) {
                        R.drawable.star_reward_glow
                    } else {
                        R.drawable.star_reward_full
                    },
                ),
            contentDescription = null,
            modifier =
                Modifier
                    .size(164.dp)
                    .graphicsLayer {
                        scaleX = starScale
                        scaleY = starScale
                        rotationZ = starRotation
                    },
        )
        StarScoreIndicator(
            star = star,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-4).dp),
        )
    }
}

@Composable
private fun RewardSparkle(
    size: Int,
    scale: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.star_reward_small),
        contentDescription = null,
        modifier =
            modifier
                .size(size.dp)
                .scale(scale)
                .alpha(alpha),
    )
}

private fun QuizAnswer.toRewardTitle(remainingRetryCount: Int): String =
    when {
        star == null -> "확인하고 있어요"
        isCorrect == true -> "잘했어요!"
        remainingRetryCount > 0 -> "한 번 더 해볼까요?"
        else -> "괜찮아요!"
    }

private fun QuizAnswer.toRewardDescription(remainingRetryCount: Int): String =
    when {
        star == null -> "목소리를 듣고 별을 세고 있어요."
        isCorrect == true -> "별을 멋지게 모았어요."
        remainingRetryCount > 0 -> "천천히 다시 말하면 돼요."
        else -> "다음 문제에서 또 해봐요."
    }

private fun QuizAnswer.toFeedbackMascot(remainingRetryCount: Int): SudaMascot =
    when {
        star == null -> SudaMascot.Loading
        isCorrect == true -> SudaMascot.Success3Star
        remainingRetryCount > 0 -> SudaMascot.Retry1Star
        else -> SudaMascot.Good2Star
    }

private fun Int?.toStarBadgeText(): String =
    this
        ?.coerceIn(MIN_STAR, MAX_STAR)
        ?.let { "별 $it / $MAX_STAR" }
        ?: "채점 중"

private fun Int?.toStarBadgeTone(): AppBadgeTone =
    when (this?.coerceIn(MIN_STAR, MAX_STAR)) {
        null -> AppBadgeTone.Neutral
        MAX_STAR -> AppBadgeTone.Success
        PASSING_STAR -> AppBadgeTone.Warning
        else -> AppBadgeTone.Error
    }

private fun QuizAnswer.feedbackEventKey(): String? =
    star?.let { "${questionId}_${attemptCount}_$it" }

private fun QuizAnswer.toFeedbackCue(): QuizFeedbackCue? =
    when {
        star == null -> null
        isCorrect == true -> QuizFeedbackCue.Correct
        else -> QuizFeedbackCue.Retry
    }

private const val MIN_STAR = 0
private const val PASSING_STAR = 2
private const val MAX_STAR = 3
private const val EMPTY_STAR_ALPHA = 0.24f
