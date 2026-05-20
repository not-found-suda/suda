@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
        val earnedStarCount = answer.normalizedStarCount()
        val isFailure = answer.isFailureResult()

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isFailure) {
                FailureRewardBurst()
            } else {
                StarRewardBurst(
                    eventKey = eventKey,
                    starCount = earnedStarCount,
                )
            }
            QuizFeedbackMascotMessage(
                mascot = answer.toFeedbackMascot(),
                title = answer.toRewardTitle(),
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
            modifier = Modifier.size(58.dp),
        )
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
private fun FailureRewardBurst(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .size(REWARD_BURST_SIZE.dp),
        contentAlignment = Alignment.Center,
    ) {
        SudaMascotImage(
            mascot = SudaMascot.ErrorNetwork,
            contentDescription = null,
            modifier =
                Modifier
                    .size(FAILURE_MASCOT_SIZE.dp)
                    .offset(y = (-8).dp),
        )
        AppBadge(
            text = "\uC544\uC26C\uC6CC\uC694",
            tone = AppBadgeTone.Error,
            modifier = Modifier.align(Alignment.BottomCenter),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StarRewardBurst(
    eventKey: String?,
    starCount: Int,
    modifier: Modifier = Modifier,
) {
    val scale = remember(eventKey) { Animatable(0.7f) }
    val rotation = remember(eventKey) { Animatable(-10f) }

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
        starCount = starCount,
        starScale = scale.value,
        starRotation = rotation.value,
        modifier = modifier,
    )
}

@Composable
private fun StarRewardBurstContent(
    starCount: Int,
    starScale: Float,
    starRotation: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .size(REWARD_BURST_SIZE.dp),
        contentAlignment = Alignment.Center,
    ) {
        LargeStarRow(
            starCount = starCount,
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = starScale
                        scaleY = starScale
                        rotationZ = starRotation
                    },
        )
    }
}

@Composable
private fun LargeStarRow(
    starCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BIG_STAR_SPACING.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(starCount.coerceIn(1, MAX_STAR)) {
            Image(
                painter = painterResource(R.drawable.star_reward_big),
                contentDescription = null,
                modifier = Modifier.size(BIG_STAR_SIZE.dp),
            )
        }
    }
}

private fun QuizAnswer.normalizedStarCount(): Int = star?.coerceIn(MIN_STAR, MAX_STAR) ?: MIN_STAR

private fun QuizAnswer.isFailureResult(): Boolean = star != null && normalizedStarCount() == MIN_STAR

private fun QuizAnswer.hasEarnedStar(): Boolean = normalizedStarCount() > MIN_STAR

private fun QuizAnswer.earnedStarText(): String =
    when (normalizedStarCount()) {
        1 -> "\uBCC4 1\uAC1C"
        2 -> "\uBCC4 2\uAC1C"
        3 -> "\uBCC4 3\uAC1C"
        else -> "\uBCC4 0\uAC1C"
    }

private fun QuizAnswer.toRewardTitle(): String =
    when {
        star == null -> "\uD655\uC778\uD558\uACE0 \uC788\uC5B4\uC694"
        normalizedStarCount() == 1 -> "\uC88B\uC544\uC694!"
        normalizedStarCount() == 2 -> "\uCC38 \uC798\uD588\uC5B4\uC694!"
        normalizedStarCount() == MAX_STAR -> "\uC644\uBCBD\uD574\uC694!"
        else -> "\uC544\uC26C\uC6CC\uC694 \u3160\u3160"
    }

private fun QuizAnswer.toRewardDescription(remainingRetryCount: Int): String =
    when {
        star == null -> "\uBAA9\uC18C\uB9AC\uB97C \uB4E3\uACE0 \uBCC4\uC744 \uC138\uACE0 \uC788\uC5B4\uC694."
        normalizedStarCount() == MAX_STAR -> "\uBCC4\uC744 \uBA4B\uC9C0\uAC8C \uBAA8\uC558\uC5B4\uC694."
        normalizedStarCount() == 2 && remainingRetryCount > 0 ->
            "\uBCC4 2\uAC1C\uC608\uC694. " +
                "\uD55C \uBC88 \uB354 \uB9D0\uD574\uBCF4\uBA74 " +
                "3\uAC1C\uAE4C\uC9C0 \uBC1B\uC744 \uC218 \uC788\uC5B4\uC694."
        normalizedStarCount() == 1 && remainingRetryCount > 0 ->
            "\uBCC4 1\uAC1C\uC608\uC694. " +
                "\uD55C \uBC88 \uB354 \uB9D0\uD574\uBCF4\uBA74 " +
                "\uB354 \uB9CE\uC740 \uBCC4\uC744 \uBC1B\uC744 \uC218 \uC788\uC5B4\uC694."
        hasEarnedStar() -> "${earnedStarText()}\uB97C \uBC1B\uC558\uC5B4\uC694."
        remainingRetryCount > 0 ->
            "\uB2E4\uC2DC \uB9D0\uD574\uBCF4\uAC70\uB098 " +
                "\uB2E4\uC74C \uBB38\uC81C\uB85C \uB118\uC5B4\uAC08 \uC218 \uC788\uC5B4\uC694."
        else -> "\uB2E4\uC74C \uBB38\uC81C\uC5D0\uC11C \uB2E4\uC2DC \uD574\uBD10\uC694."
    }

private fun QuizAnswer.toFeedbackMascot(): SudaMascot =
    when {
        star == null -> SudaMascot.Loading
        normalizedStarCount() == MAX_STAR -> SudaMascot.Success3Star
        normalizedStarCount() == 2 -> SudaMascot.Good2Star
        normalizedStarCount() == 1 -> SudaMascot.Retry1Star
        else -> SudaMascot.ErrorNetwork
    }

private fun QuizAnswer.feedbackEventKey(): String? =
    star?.let { "${questionId}_${attemptCount}_$it" }

private fun QuizAnswer.toFeedbackCue(): QuizFeedbackCue? =
    when {
        star == null -> null
        hasEarnedStar() -> QuizFeedbackCue.Correct
        else -> QuizFeedbackCue.Retry
    }

private const val MIN_STAR = 0
private const val MAX_STAR = 3
private const val REWARD_BURST_SIZE = 152
private const val BIG_STAR_SIZE = 46
private const val BIG_STAR_SPACING = 6
private const val FAILURE_MASCOT_SIZE = 112
