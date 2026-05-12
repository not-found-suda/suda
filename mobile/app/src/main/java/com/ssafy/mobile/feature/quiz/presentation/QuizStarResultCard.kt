@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer

@Composable
internal fun QuizStarResultCard(
    answer: QuizAnswer,
    remainingRetryCount: Int,
    modifier: Modifier = Modifier,
) {
    val resultText = answer.toStarResultText(remainingRetryCount)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = answer.star.starResultContainerColor(),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = answer.star.toStarDisplay(),
                style = MaterialTheme.typography.headlineMedium,
                color = answer.star.starResultColor(),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                text = resultText.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = resultText.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "인식 결과: ${answer.sttText}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class StarResultText(
    val title: String,
    val description: String,
)

private fun QuizAnswer.toStarResultText(remainingRetryCount: Int): StarResultText =
    when (star?.coerceIn(MIN_STAR, MAX_STAR)) {
        null ->
            StarResultText(
                title = "답변을 확인하고 있어요",
                description = "잠시만 기다려 주세요. 채점 결과가 곧 도착해요.",
            )
        MAX_STAR ->
            StarResultText(
                title = "정말 잘했어요!",
                description = "단어를 또렷하게 말했어요. 다음 문제로 가볼까요?",
            )
        TWO_STARS ->
            StarResultText(
                title = "거의 다 왔어요!",
                description = retryDescription(remainingRetryCount),
            )
        else ->
            StarResultText(
                title = "다시 한 번 해봐요!",
                description = retryDescription(remainingRetryCount),
            )
    }

private fun retryDescription(remainingRetryCount: Int): String =
    if (remainingRetryCount > 0) {
        "천천히 듣고 다시 말해볼 수 있어요. ${remainingRetryCount}번 더 도전할 수 있어요."
    } else {
        "충분히 연습했어요. 다음 문제로 넘어가도 괜찮아요."
    }

@Composable
private fun Int?.starResultContainerColor(): Color =
    when (this?.coerceIn(MIN_STAR, MAX_STAR)) {
        null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        MAX_STAR -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        TWO_STARS -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
    }

@Composable
private fun Int?.starResultColor(): Color =
    when (this?.coerceIn(MIN_STAR, MAX_STAR)) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant
        MAX_STAR -> MaterialTheme.colorScheme.primary
        TWO_STARS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

private fun Int?.toStarDisplay(): String {
    val filledCount = this?.coerceIn(MIN_STAR, MAX_STAR) ?: 0
    return buildString {
        repeat(MAX_STAR) { index ->
            append(if (index < filledCount) FILLED_STAR else EMPTY_STAR)
            if (index < MAX_STAR - 1) append(STAR_SEPARATOR)
        }
    }
}

private const val MIN_STAR = 1
private const val TWO_STARS = 2
private const val MAX_STAR = 3
private const val FILLED_STAR = '★'
private const val EMPTY_STAR = '☆'
private const val STAR_SEPARATOR = ' '
