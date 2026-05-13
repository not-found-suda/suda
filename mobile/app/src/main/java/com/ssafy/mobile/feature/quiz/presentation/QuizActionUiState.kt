package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppCard
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer

internal data class QuizActionUiState(
    val recordButton: QuizButtonUiState,
    val nextButton: QuizButtonUiState,
    val recording: QuizRecordingUiState,
    val answerSubmitState: QuizAnswerSubmitState,
)

internal data class QuizButtonUiState(
    val text: String,
    val enabled: Boolean,
)

internal data class QuizRecordingUiState(
    val status: QuizRecordingStatus,
    val answerAttemptCount: Int?,
    val recognizedText: String?,
)

@Suppress("LongParameterList")
internal fun quizActionUiState(
    answer: QuizAnswer?,
    recordingStatus: QuizRecordingStatus,
    answerSubmitState: QuizAnswerSubmitState,
    isRecording: Boolean,
    isProcessing: Boolean,
    isSubmitting: Boolean,
    isCompletionPending: Boolean,
    isSaveFailed: Boolean,
    canSkipQuestion: Boolean,
    hasAnswered: Boolean,
    hasSuccessfulAnswer: Boolean,
    retryLimitReached: Boolean,
    remainingRetryCount: Int,
    canRecordAnswer: Boolean,
    canMoveNext: Boolean,
    isLastQuestion: Boolean,
): QuizActionUiState =
    QuizActionUiState(
        recordButton =
            QuizButtonUiState(
                text =
                    quizRecordButtonText(
                        isRecording = isRecording,
                        isProcessing = isProcessing,
                        isSubmitting = isSubmitting,
                        isCompletionPending = isCompletionPending,
                        isSaveFailed = isSaveFailed,
                        hasAnswered = hasAnswered,
                        remainingRetryCount = remainingRetryCount,
                    ),
                enabled = canRecordAnswer,
            ),
        nextButton =
            QuizButtonUiState(
                text =
                    quizNextActionText(
                        isCompletionPending = isCompletionPending,
                        isSaveFailed = isSaveFailed,
                        canSkipQuestion = canSkipQuestion,
                        hasAnswered = hasAnswered,
                        hasSuccessfulAnswer = hasSuccessfulAnswer,
                        retryLimitReached = retryLimitReached,
                        isLastQuestion = isLastQuestion,
                    ),
                enabled = canMoveNext,
            ),
        recording =
            QuizRecordingUiState(
                status = recordingStatus,
                answerAttemptCount = answer?.attemptCount,
                recognizedText = answer?.sttText,
            ),
        answerSubmitState = answerSubmitState,
    )

@Composable
internal fun QuizActionCard(
    actionState: QuizActionUiState,
    onAnswerClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        QuizRecordingStatusText(
            recordingStatus = actionState.recording.status,
            answerAttemptCount = actionState.recording.answerAttemptCount,
            recognizedText = actionState.recording.recognizedText,
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppPrimaryButton(
            text = actionState.recordButton.text,
            onClick = onAnswerClick,
            enabled = actionState.recordButton.enabled,
        )

        Spacer(modifier = Modifier.height(10.dp))

        AppSecondaryButton(
            text = actionState.nextButton.text,
            onClick = onNextClick,
            enabled = actionState.nextButton.enabled,
        )

        AnswerSubmitStatusBadge(
            state = actionState.answerSubmitState,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun AnswerSubmitStatusBadge(
    state: QuizAnswerSubmitState,
    modifier: Modifier = Modifier,
) {
    val message = state.toStatusMessage() ?: return

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AppBadge(
            text = message,
            tone = state.toBadgeTone(),
        )
    }
}

private fun QuizAnswerSubmitState.toStatusMessage(): String? =
    when (this) {
        QuizAnswerSubmitState.Idle -> null
        QuizAnswerSubmitState.Submitting -> "답변을 저장하고 있어요..."
        QuizAnswerSubmitState.Success -> "답변을 저장했어요."
        is QuizAnswerSubmitState.CompletionPending -> message
        is QuizAnswerSubmitState.SaveFailed -> message
        is QuizAnswerSubmitState.Error -> message
    }

private fun QuizAnswerSubmitState.toBadgeTone(): AppBadgeTone =
    when (this) {
        QuizAnswerSubmitState.Success -> AppBadgeTone.Success
        QuizAnswerSubmitState.Submitting -> AppBadgeTone.Primary
        QuizAnswerSubmitState.Idle -> AppBadgeTone.Neutral
        is QuizAnswerSubmitState.CompletionPending,
        is QuizAnswerSubmitState.SaveFailed,
        is QuizAnswerSubmitState.Error,
        -> AppBadgeTone.Error
    }

private fun quizRecordButtonText(
    isRecording: Boolean,
    isProcessing: Boolean,
    isSubmitting: Boolean,
    isCompletionPending: Boolean,
    isSaveFailed: Boolean,
    hasAnswered: Boolean,
    remainingRetryCount: Int,
): String =
    when {
        isRecording -> "말하기 완료"
        isProcessing -> "답변 준비 중..."
        isSubmitting -> "답변 저장 중..."
        isCompletionPending -> "답변 저장 완료"
        isSaveFailed -> quizRetryButtonText(remainingRetryCount)
        hasAnswered -> quizRetryButtonText(remainingRetryCount)
        else -> "말하기"
    }

private fun quizNextActionText(
    isCompletionPending: Boolean,
    isSaveFailed: Boolean,
    canSkipQuestion: Boolean,
    hasAnswered: Boolean,
    hasSuccessfulAnswer: Boolean,
    retryLimitReached: Boolean,
    isLastQuestion: Boolean,
): String =
    when {
        isCompletionPending -> "결과 보기 다시 시도"
        isSaveFailed -> "답변 저장 다시 시도"
        else ->
            quizNextButtonText(
                canSkipQuestion = canSkipQuestion,
                hasAnswered = hasAnswered,
                hasSuccessfulAnswer = hasSuccessfulAnswer,
                retryLimitReached = retryLimitReached,
                isLastQuestion = isLastQuestion,
            )
    }
