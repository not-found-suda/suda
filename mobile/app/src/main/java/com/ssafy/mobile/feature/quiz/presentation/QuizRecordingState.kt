package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

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
    Text(
        text = recordingStatus.message(answerAttemptCount, recognizedText),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = recordingStatus.textColor(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun QuizRecordingStatus.textColor() =
    when (this) {
        QuizRecordingStatus.Completed -> MaterialTheme.colorScheme.primary
        QuizRecordingStatus.NoSpeech,
        QuizRecordingStatus.Timeout,
        QuizRecordingStatus.PermissionError,
        -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
        QuizRecordingStatus.Processing -> "말한 단어를 인식하고 있어요."
        QuizRecordingStatus.Completed ->
            if (recognizedText.isNullOrBlank()) {
                "단어를 인식했어요."
            } else {
                "인식 결과: $recognizedText"
            }
        QuizRecordingStatus.NoSpeech -> "목소리를 잘 듣지 못했어요. 다시 말해볼까요?"
        QuizRecordingStatus.Timeout -> "입력이 없어 인식을 종료했어요. 다시 말할 수 있어요."
        QuizRecordingStatus.PermissionError -> "마이크 권한을 확인한 뒤 다시 시도해 주세요."
    }
