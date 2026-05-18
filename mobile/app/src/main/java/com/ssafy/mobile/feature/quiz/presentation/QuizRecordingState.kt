@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadgeTone

enum class QuizRecordingStatus {
    Idle,
    Recording,
    FallbackRecording,
    Processing,
    Completed,
    NoSpeech,
    Timeout,
    PermissionError,
}

internal val QuizRecordingStatus.canSkipQuestion: Boolean
    get() =
        this == QuizRecordingStatus.NoSpeech ||
            this == QuizRecordingStatus.Timeout ||
            this == QuizRecordingStatus.PermissionError

internal class QuizRecordingController(
    val status: QuizRecordingStatus,
    val onRecordButtonClick: () -> Unit,
    val reset: () -> Unit,
)

@Composable
@Suppress("FunctionNaming")
internal fun QuizRecordingStatusText(
    recordingStatus: QuizRecordingStatus,
    answerAttemptCount: Int?,
    recognizedText: String?,
    modifier: Modifier = Modifier,
) {
    val colors = recordingStatus.statusColors()
    val message = recordingStatus.message(answerAttemptCount, recognizedText)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = colors.container,
            contentColor = colors.content,
            border =
                BorderStroke(
                    width = 1.dp,
                    color = colors.content.copy(alpha = 0.16f),
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuizRecordingPulseDot(
                    active = recordingStatus.isRecording,
                    color = colors.content,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recordingStatus.title(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Crossfade(
                        targetState = message,
                        label = "quizRecordingStatusMessage",
                    ) { statusMessage ->
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.content.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizRecordingPulseDot(
    active: Boolean,
    color: Color,
) {
    val transition = rememberInfiniteTransition(label = "quizRecordingPulse")
    val scale by
        transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.24f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = PULSE_DURATION_MILLIS),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "quizRecordingPulseScale",
        )
    val alpha by
        transition.animateFloat(
            initialValue = 0.58f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = PULSE_DURATION_MILLIS),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "quizRecordingPulseAlpha",
        )

    Box(
        modifier =
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .graphicsLayer {
                    scaleX = if (active) scale else 1f
                    scaleY = if (active) scale else 1f
                    this.alpha = if (active) alpha else 0.78f
                },
    )
}

private fun QuizRecordingStatus.badgeTone(): AppBadgeTone =
    when (this) {
        QuizRecordingStatus.Recording,
        QuizRecordingStatus.FallbackRecording,
        -> AppBadgeTone.Error
        QuizRecordingStatus.Completed -> AppBadgeTone.Success
        QuizRecordingStatus.NoSpeech,
        QuizRecordingStatus.Timeout,
        QuizRecordingStatus.PermissionError,
        -> AppBadgeTone.Error
        QuizRecordingStatus.Processing -> AppBadgeTone.Primary
        QuizRecordingStatus.Idle -> AppBadgeTone.Neutral
    }

private val QuizRecordingStatus.isRecording: Boolean
    get() =
        this == QuizRecordingStatus.Recording ||
            this == QuizRecordingStatus.FallbackRecording

private fun QuizRecordingStatus.title(): String =
    when (this) {
        QuizRecordingStatus.Idle -> "준비됐어요"
        QuizRecordingStatus.Recording,
        QuizRecordingStatus.FallbackRecording,
        -> "듣고 있어요"
        QuizRecordingStatus.Processing -> "확인하고 있어요"
        QuizRecordingStatus.Completed -> "답변을 받았어요"
        QuizRecordingStatus.NoSpeech,
        QuizRecordingStatus.Timeout,
        -> "한 번 더 해볼까요?"
        QuizRecordingStatus.PermissionError -> "마이크 권한이 필요해요"
    }

@Composable
private fun QuizRecordingStatus.statusColors(): RecordingStatusColors =
    when (badgeTone()) {
        AppBadgeTone.Success ->
            RecordingStatusColors(
                container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                content = MaterialTheme.colorScheme.primary,
            )
        AppBadgeTone.Error ->
            RecordingStatusColors(
                container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.56f),
                content = MaterialTheme.colorScheme.error,
            )
        AppBadgeTone.Primary ->
            RecordingStatusColors(
                container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
                content = MaterialTheme.colorScheme.tertiary,
            )
        else ->
            RecordingStatusColors(
                container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }

private fun QuizRecordingStatus.message(
    answerAttemptCount: Int?,
    recognizedText: String?,
): String =
    when (this) {
        QuizRecordingStatus.Idle ->
            if (answerAttemptCount == null) {
                "버튼을 누르고 단어를 말해 주세요."
            } else {
                "$answerAttemptCount 번째 답변이에요. 다시 말해볼까요?"
            }
        QuizRecordingStatus.Recording -> "듣고 있어요. 다 말했으면 버튼을 눌러 주세요."
        QuizRecordingStatus.FallbackRecording ->
            "서버 인식이 어려워 기기에서 다시 듣고 있어요. 한 번 더 말해 주세요."
        QuizRecordingStatus.Processing -> "녹음 파일을 준비하고 있어요."
        QuizRecordingStatus.Completed ->
            if (recognizedText.isNullOrBlank()) {
                "녹음을 저장했어요. 답변을 확인하고 있어요."
            } else {
                "인식 결과: $recognizedText"
            }
        QuizRecordingStatus.NoSpeech -> "목소리를 잘 듣지 못했어요. 다시 말해볼까요?"
        QuizRecordingStatus.Timeout -> "입력이 없어 인식을 종료했어요. 다시 말할 수 있어요."
        QuizRecordingStatus.PermissionError -> "마이크 권한을 확인한 뒤 다시 시도해 주세요."
    }

private data class RecordingStatusColors(
    val container: Color,
    val content: Color,
)

private const val PULSE_DURATION_MILLIS = 680
