package com.ssafy.mobile.feature.quiz.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.ssafy.mobile.core.audio.AndroidAudioRecorder
import kotlinx.coroutines.delay

internal enum class QuizRecordingStatus {
    Idle,
    Recording,
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
@Suppress("CyclomaticComplexMethod")
internal fun rememberQuizRecordingController(
    currentQuestionId: Long?,
    onSubmitRecording: () -> Unit,
): QuizRecordingController {
    val context = LocalContext.current
    val submitRecording by rememberUpdatedState(onSubmitRecording)
    val recorder =
        remember(context) {
            AndroidAudioRecorder(context.applicationContext)
        }
    var status by remember {
        mutableStateOf(QuizRecordingStatus.Idle)
    }
    var recordingStartedAtMs by remember {
        mutableStateOf(0L)
    }
    var hasDetectedVoice by remember {
        mutableStateOf(false)
    }

    fun resetRecording() {
        recorder.release()
        status = QuizRecordingStatus.Idle
        recordingStartedAtMs = 0L
        hasDetectedVoice = false
    }

    DisposableEffect(recorder) {
        onDispose {
            recorder.release()
        }
    }

    LaunchedEffect(currentQuestionId) {
        resetRecording()
    }

    LaunchedEffect(status, recordingStartedAtMs) {
        if (status != QuizRecordingStatus.Recording) return@LaunchedEffect

        while (status == QuizRecordingStatus.Recording) {
            delay(RECORDING_POLL_INTERVAL_MS)
            if (recorder.getMaxAmplitude() >= VOICE_AMPLITUDE_THRESHOLD) {
                hasDetectedVoice = true
            }
            if (System.currentTimeMillis() - recordingStartedAtMs >= RECORDING_MAX_DURATION_MS) {
                val file = recorder.stop()
                if (file != null && hasDetectedVoice) {
                    status = QuizRecordingStatus.Completed
                    submitRecording()
                } else {
                    status = QuizRecordingStatus.Timeout
                }
            }
        }
    }

    fun startRecording() {
        if (!context.hasRecordAudioPermission()) {
            status = QuizRecordingStatus.PermissionError
            return
        }

        val questionId = currentQuestionId ?: return
        val startedAtMs = System.currentTimeMillis()
        val started = recorder.start(fileName = "quiz_answer_${questionId}_$startedAtMs")

        if (started) {
            hasDetectedVoice = false
            recordingStartedAtMs = startedAtMs
            status = QuizRecordingStatus.Recording
        } else {
            status = QuizRecordingStatus.PermissionError
        }
    }

    fun stopRecording() {
        status = QuizRecordingStatus.Processing
        val recordedDurationMs = System.currentTimeMillis() - recordingStartedAtMs
        val file = recorder.stop()
        val isValidRecording =
            file != null &&
                hasDetectedVoice &&
                recordedDurationMs >= RECORDING_MIN_DURATION_MS

        if (isValidRecording) {
            status = QuizRecordingStatus.Completed
            submitRecording()
        } else {
            status = QuizRecordingStatus.NoSpeech
        }
    }

    return QuizRecordingController(
        status = status,
        onRecordButtonClick = {
            when (status) {
                QuizRecordingStatus.Recording -> stopRecording()
                QuizRecordingStatus.Processing -> Unit
                else -> startRecording()
            }
        },
        reset = ::resetRecording,
    )
}

@Composable
internal fun QuizRecordingStatusText(
    recordingStatus: QuizRecordingStatus,
    answerAttemptCount: Int?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = recordingStatus.message(answerAttemptCount),
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

private fun QuizRecordingStatus.message(answerAttemptCount: Int?): String =
    when (this) {
        QuizRecordingStatus.Idle ->
            if (answerAttemptCount == null) {
                "버튼을 누르고 단어를 말해 주세요."
            } else {
                "$answerAttemptCount 번째 답변이 임시 저장됐어요."
            }
        QuizRecordingStatus.Recording -> "듣고 있어요. 다 말했으면 버튼을 눌러 주세요."
        QuizRecordingStatus.Processing -> "녹음 파일을 확인하고 있어요."
        QuizRecordingStatus.Completed ->
            if (answerAttemptCount == null) {
                "녹음이 완료됐어요."
            } else {
                "$answerAttemptCount 번째 답변이 임시 저장됐어요."
            }
        QuizRecordingStatus.NoSpeech -> "목소리가 잘 들리지 않았어요. 다시 말해볼까요?"
        QuizRecordingStatus.Timeout -> "시간이 끝나 녹음을 멈췄어요. 다시 말할 수 있어요."
        QuizRecordingStatus.PermissionError -> "마이크 권한을 확인한 뒤 다시 시도해 주세요."
    }

private fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

private const val RECORDING_MAX_DURATION_MS = 5_000L
private const val RECORDING_MIN_DURATION_MS = 500L
private const val RECORDING_POLL_INTERVAL_MS = 250L
private const val VOICE_AMPLITUDE_THRESHOLD = 1_500
