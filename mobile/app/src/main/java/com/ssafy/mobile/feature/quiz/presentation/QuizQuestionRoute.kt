@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongParameterList",
    "MagicNumber",
)

package com.ssafy.mobile.feature.quiz.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppBadge
import com.ssafy.mobile.core.ui.components.AppBadgeTone
import com.ssafy.mobile.core.ui.components.AppNetworkImage
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer
import com.ssafy.mobile.feature.quiz.domain.model.QuizQuestion
import com.ssafy.mobile.feature.quiz.domain.model.QuizRetryPolicy
import com.ssafy.mobile.feature.quiz.domain.model.QuizSessionState
import kotlin.math.roundToInt

@Composable
fun quizQuestionRoute(
    onNavigateToResult: (Long, Long, String) -> Unit,
    modifier: Modifier = Modifier,
    quizViewModel: QuizQuestionViewModel = hiltViewModel(),
    recordingViewModel: QuizRecordingViewModel = hiltViewModel(),
) {
    val quizState by quizViewModel.quizState.collectAsStateWithLifecycle()
    val answerSubmitState by quizViewModel.answerSubmitState.collectAsStateWithLifecycle()
    val recordingStatus by recordingViewModel.status.collectAsStateWithLifecycle()
    val recordedAudio by recordingViewModel.recordedAudio.collectAsStateWithLifecycle()
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

    LaunchedEffect(recordedAudio?.eventId) {
        val audio = recordedAudio ?: return@LaunchedEffect
        quizViewModel.submitRecordedAudio(
            audioFile = audio.file,
            audioMimeType = audio.mimeType,
        )
        recordingViewModel.consumeRecordedAudio()
    }

    LaunchedEffect(quizState.isFinished) {
        if (quizState.isFinished) {
            val sessionId = quizState.sessionId
            if (sessionId != null) {
                onNavigateToResult(
                    sessionId,
                    quizViewModel.categoryId,
                    quizViewModel.difficulty,
                )
            }
        }
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
                    visual = QuizMessageVisual.Loading,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.errorMessage != null -> {
                QuizMessageState(
                    title = "퀴즈를 불러오지 못했어요",
                    description = state.errorMessage,
                    actionText = "다시 시도",
                    onActionClick = onRetryClick,
                    visual = QuizMessageVisual.Error,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.questions.isEmpty() -> {
                QuizMessageState(
                    title = "준비된 문제가 없어요",
                    description = "이 카테고리에 준비된 퀴즈 문제가 아직 없어요.",
                    visual = QuizMessageVisual.Empty,
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

        AnimatedContent(
            targetState = question,
            label = "quizQuestionContent",
        ) { currentQuestion ->
            QuizQuestionCard(question = currentQuestion)
        }

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
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "quizProgress",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBadge(
                text = "문제 $currentQuestionNumber / $totalQuestionCount",
                tone = AppBadgeTone.Primary,
            )
            AppBadge(
                text = "${(animatedProgress * PERCENT_MAX).roundToInt()}%",
                tone = AppBadgeTone.Neutral,
            )
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.12f)
                        .clip(RoundedCornerShape(8.dp))
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

            QuizTargetWord(question = question)
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
private fun QuizTargetWord(
    question: QuizQuestion,
    modifier: Modifier = Modifier,
) {
    val targetText =
        question.word.takeIf { it.isNotBlank() } ?: "그림을 보고 단어를 말해 주세요"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppBadge(
            text = "그림을 보고 말해요",
            tone = AppBadgeTone.Primary,
        )
        Text(
            text = targetText,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "준비되면 아래 버튼을 눌러 말해 주세요",
            style = MaterialTheme.typography.bodyMedium,
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
    val isSaveFailed = answerSubmitState is QuizAnswerSubmitState.SaveFailed
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
        (
            isCompletionPending ||
                isSaveFailed ||
                QuizRetryPolicy.canMoveNext(answer, canSkipQuestion)
        ) &&
            !isRecording &&
            !isProcessing &&
            !isSubmitting
    val actionState =
        quizActionUiState(
            answer = answer,
            recordingStatus = recordingStatus,
            answerSubmitState = answerSubmitState,
            isRecording = isRecording,
            isProcessing = isProcessing,
            isSubmitting = isSubmitting,
            isCompletionPending = isCompletionPending,
            isSaveFailed = isSaveFailed,
            canSkipQuestion = canSkipQuestion,
            hasAnswered = hasAnswered,
            hasSuccessfulAnswer = hasSuccessfulAnswer,
            retryLimitReached = retryLimitReached,
            remainingRetryCount = remainingRetryCount,
            canRecordAnswer = canRecordAnswer,
            canMoveNext = canMoveNext,
            isLastQuestion = isLastQuestion,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (answer != null) {
            QuizStarResultCard(
                answer = answer,
                remainingRetryCount = remainingRetryCount,
            )
        }

        QuizActionCard(
            actionState = actionState,
            onAnswerClick = onAnswerClick,
            onNextClick = onNextClick,
        )
    }
}

private const val PERCENT_MAX = 100
private const val FIRST_LETTER_COUNT = 1
