@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongParameterList",
    "MagicNumber",
)

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppNetworkImage
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer
import com.ssafy.mobile.feature.quiz.domain.model.QuizQuestion
import com.ssafy.mobile.feature.quiz.domain.model.QuizRetryPolicy
import com.ssafy.mobile.feature.quiz.domain.model.QuizSessionState
import kotlin.math.roundToInt

@Composable
fun quizQuestionRoute(
    modifier: Modifier = Modifier,
    quizViewModel: QuizQuestionViewModel = hiltViewModel(),
    recordingViewModel: QuizRecordingViewModel = hiltViewModel(),
) {
    val quizState by quizViewModel.quizState.collectAsStateWithLifecycle()
    val answerSubmitState by quizViewModel.answerSubmitState.collectAsStateWithLifecycle()
    val recordingStatus by recordingViewModel.status.collectAsStateWithLifecycle()
    val recognizedText by recordingViewModel.recognizedText.collectAsStateWithLifecycle()
    val recordingController =
        QuizRecordingController(
            status = recordingStatus,
            onRecordButtonClick = {
                when (recordingStatus) {
                    QuizRecordingStatus.Recording,
                    QuizRecordingStatus.FallbackRecording,
                    -> recordingViewModel.stopListening()

                    QuizRecordingStatus.Processing -> Unit
                    else -> recordingViewModel.startListening()
                }
            },
            reset = recordingViewModel::reset,
        )

    LaunchedEffect(quizState.currentQuestion?.id) {
        recordingViewModel.reset()
    }

    LaunchedEffect(recognizedText) {
        val sttText = recognizedText ?: return@LaunchedEffect
        quizViewModel.submitRecognizedText(sttText)
        recordingViewModel.consumeRecognizedText()
    }

    QuizQuestionScreen(
        state = quizState,
        answerSubmitState = answerSubmitState,
        recordingStatus = recordingController.status,
        onAnswerClick = recordingController.onRecordButtonClick,
        onNextClick = {
            recordingController.reset()
            quizViewModel.moveToNextQuestion()
        },
        onRestartClick = {
            recordingController.reset()
            quizViewModel.restart()
        },
        onRetryClick = {
            recordingController.reset()
            quizViewModel.retry()
        },
        modifier = modifier,
    )
}

@Composable
private fun QuizQuestionScreen(
    state: QuizSessionState,
    answerSubmitState: QuizAnswerSubmitState,
    recordingStatus: QuizRecordingStatus,
    onAnswerClick: () -> Unit,
    onNextClick: () -> Unit,
    onRestartClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            state.isLoading -> {
                QuizMessageState(
                    title = "퀴즈를 불러오는 중이에요",
                    description = "잠시만 기다려 주세요.",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.errorMessage != null -> {
                QuizMessageState(
                    title = "퀴즈를 불러오지 못했어요",
                    description = state.errorMessage,
                    actionText = "다시 시도",
                    onActionClick = onRetryClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.questions.isEmpty() -> {
                QuizMessageState(
                    title = "준비된 문제가 없어요",
                    description = "이 카테고리에 준비된 퀴즈 문제가 아직 없어요.",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.isFinished -> {
                QuizFinishedState(
                    solvedCount = state.answers.size,
                    totalCount = state.totalQuestionCount,
                    onRestartClick = onRestartClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                QuizQuestionContent(
                    state = state,
                    question = requireNotNull(state.currentQuestion),
                    answerSubmitState = answerSubmitState,
                    recordingStatus = recordingStatus,
                    onAnswerClick = onAnswerClick,
                    onNextClick = onNextClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun QuizQuestionContent(
    state: QuizSessionState,
    question: QuizQuestion,
    answerSubmitState: QuizAnswerSubmitState,
    recordingStatus: QuizRecordingStatus,
    onAnswerClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        QuizProgressHeader(
            currentQuestionNumber = state.currentQuestionNumber,
            totalQuestionCount = state.totalQuestionCount,
            progress = state.progress,
        )

        QuizQuestionCard(question = question)

        QuizPrompt(question = question)

        QuizActionArea(
            isLastQuestion = state.isLastQuestion,
            recordingStatus = recordingStatus,
            answerSubmitState = answerSubmitState,
            answer =
                state.answers.firstOrNull {
                    it.questionId == question.id
                },
            onAnswerClick = onAnswerClick,
            onNextClick = onNextClick,
        )
    }
}

@Composable
private fun QuizProgressHeader(
    currentQuestionNumber: Int,
    totalQuestionCount: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "문제 $currentQuestionNumber / $totalQuestionCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${(progress.coerceIn(0f, 1f) * PERCENT_MAX).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun QuizQuestionCard(
    question: QuizQuestion,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f)
                    .padding(18.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                                ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            AppNetworkImage(
                imageUrl = question.imageUrl,
                contentDescription = question.word,
                fallbackText = question.word,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                placeholder = {
                    QuizImagePlaceholder(
                        word = question.word,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
    }
}

@Composable
private fun QuizImagePlaceholder(
    word: String,
    modifier: Modifier = Modifier,
) {
    val displayText = word.takeIf { it.isNotBlank() }?.take(FIRST_LETTER_COUNT) ?: "?"

    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "그림 카드",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuizPrompt(
    question: QuizQuestion,
    modifier: Modifier = Modifier,
) {
    val targetText =
        question.word.takeIf { it.isNotBlank() } ?: "그림을 보고 단어를 말해 주세요"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = targetText,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "음성 인식 후 답변을 저장할게요",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuizActionArea(
    isLastQuestion: Boolean,
    recordingStatus: QuizRecordingStatus,
    answerSubmitState: QuizAnswerSubmitState,
    answer: QuizAnswer?,
    onAnswerClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAnswered = answer != null
    val isRecording =
        recordingStatus == QuizRecordingStatus.Recording ||
            recordingStatus == QuizRecordingStatus.FallbackRecording
    val isProcessing = recordingStatus == QuizRecordingStatus.Processing
    val isSubmitting = answerSubmitState == QuizAnswerSubmitState.Submitting
    val isCompletionPending = answerSubmitState is QuizAnswerSubmitState.CompletionPending
    val canSkipQuestion = false
    val hasSuccessfulAnswer = QuizRetryPolicy.hasSuccessfulAnswer(answer)
    val remainingRetryCount = QuizRetryPolicy.remainingRetryCount(answer)
    val retryLimitReached = QuizRetryPolicy.isRetryLimitReached(answer)
    val canRecordAnswer =
        !isProcessing &&
            !isSubmitting &&
            !isCompletionPending &&
            !hasSuccessfulAnswer &&
            !retryLimitReached
    val canMoveNext =
        (isCompletionPending || QuizRetryPolicy.canMoveNext(answer, canSkipQuestion)) &&
            !isRecording &&
            !isProcessing &&
            !isSubmitting

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (answer != null) {
            QuizStarResultCard(
                answer = answer,
                remainingRetryCount = remainingRetryCount,
            )
        }

        AppPrimaryButton(
            text =
                when {
                    isRecording -> "말하기 완료"
                    isProcessing -> "음성 인식 중..."
                    isSubmitting -> "답변 저장 중..."
                    isCompletionPending -> "답변 저장 완료"
                    hasAnswered -> quizRetryButtonText(remainingRetryCount)
                    else -> "말하기"
                },
            onClick = onAnswerClick,
            enabled = canRecordAnswer,
        )

        AppSecondaryButton(
            text =
                if (isCompletionPending) {
                    "결과 보기 다시 시도"
                } else {
                    quizNextButtonText(
                        canSkipQuestion = canSkipQuestion,
                        hasAnswered = hasAnswered,
                        hasSuccessfulAnswer = hasSuccessfulAnswer,
                        retryLimitReached = retryLimitReached,
                        isLastQuestion = isLastQuestion,
                    )
                },
            onClick = onNextClick,
            enabled = canMoveNext,
        )

        QuizRecordingStatusText(
            recordingStatus = recordingStatus,
            answerAttemptCount = answer?.attemptCount,
            recognizedText = answer?.sttText,
        )

        AnswerSubmitStatusText(answerSubmitState)
    }
}

@Composable
private fun AnswerSubmitStatusText(
    state: QuizAnswerSubmitState,
    modifier: Modifier = Modifier,
) {
    val message =
        when (state) {
            QuizAnswerSubmitState.Idle -> null
            QuizAnswerSubmitState.Submitting -> "답변을 저장하고 있어요..."
            QuizAnswerSubmitState.Success -> "답변을 저장했어요."
            is QuizAnswerSubmitState.CompletionPending -> state.message
            is QuizAnswerSubmitState.Error -> state.message
        }

    if (message != null) {
        Text(
            text = message,
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (state is QuizAnswerSubmitState.Error ||
                    state is QuizAnswerSubmitState.CompletionPending
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            textAlign = TextAlign.Center,
        )
    }
}

private const val PERCENT_MAX = 100
private const val FIRST_LETTER_COUNT = 1
