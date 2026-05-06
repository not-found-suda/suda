@file:Suppress("MagicNumber")

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import com.ssafy.mobile.core.ui.components.AppSecondaryButton
import com.ssafy.mobile.feature.quiz.domain.model.MockQuizQuestions
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer
import com.ssafy.mobile.feature.quiz.domain.model.QuizQuestion
import com.ssafy.mobile.feature.quiz.domain.model.QuizSessionReducer
import com.ssafy.mobile.feature.quiz.domain.model.QuizSessionState
import kotlin.math.roundToInt

@Composable
fun quizQuestionRoute(modifier: Modifier = Modifier) {
    var quizState by remember {
        mutableStateOf(QuizSessionReducer.start(MockQuizQuestions.items))
    }
    val recordingViewModel: QuizRecordingViewModel = hiltViewModel()
    val recordingStatus by recordingViewModel.status.collectAsStateWithLifecycle()
    val recognizedText by recordingViewModel.recognizedText.collectAsStateWithLifecycle()
    val recordingController =
        QuizRecordingController(
            status = recordingStatus,
            onRecordButtonClick = {
                when (recordingStatus) {
                    QuizRecordingStatus.Recording -> recordingViewModel.stopListening()
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
        quizState = submitSttAnswer(quizState, sttText)
        recordingViewModel.consumeRecognizedText()
    }

    QuizQuestionScreen(
        state = quizState,
        recordingStatus = recordingController.status,
        onAnswerClick = recordingController.onRecordButtonClick,
        onNextClick = {
            recordingController.reset()
            quizState = QuizSessionReducer.moveToNextQuestion(quizState)
        },
        onRestartClick = {
            recordingController.reset()
            quizState = QuizSessionReducer.start(MockQuizQuestions.items)
        },
        modifier = modifier,
    )
}

@Composable
private fun QuizQuestionScreen(
    state: QuizSessionState,
    recordingStatus: QuizRecordingStatus,
    onAnswerClick: () -> Unit,
    onNextClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            state.isLoading -> {
                QuizMessageState(
                    title = "문제를 불러오는 중이에요",
                    description = "잠시만 기다려 주세요.",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.errorMessage != null -> {
                QuizMessageState(
                    title = "문제를 불러오지 못했어요",
                    description = state.errorMessage,
                    actionText = "다시 시작",
                    onActionClick = onRestartClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.questions.isEmpty() -> {
                QuizMessageState(
                    title = "준비된 문제가 없어요",
                    description = "학습할 단어가 추가되면 여기에서 시작할 수 있어요.",
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
            QuizImagePlaceholder(word = question.word)
        }
    }
}

@Composable
private fun QuizImagePlaceholder(
    word: String,
    modifier: Modifier = Modifier,
) {
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
                text = word.take(FIRST_LETTER_COUNT),
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
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = question.word,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "이 단어를 소리 내어 말해볼까요?",
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
    answer: QuizAnswer?,
    onAnswerClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAnswered = answer != null
    val isRecording = recordingStatus == QuizRecordingStatus.Recording
    val isProcessing = recordingStatus == QuizRecordingStatus.Processing
    val canSkipQuestion = recordingStatus.canSkipQuestion
    val canMoveNext = (hasAnswered || canSkipQuestion) && !isRecording && !isProcessing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppPrimaryButton(
            text =
                when {
                    isRecording -> "말하기 완료"
                    isProcessing -> "녹음 확인 중"
                    hasAnswered -> "다시 말하기"
                    else -> "답변하기"
                },
            onClick = onAnswerClick,
            enabled = !isProcessing,
        )

        AppSecondaryButton(
            text =
                when {
                    canSkipQuestion -> "이번 문제 넘어가기"
                    isLastQuestion -> "결과 보기"
                    else -> "다음 문제"
                },
            onClick = onNextClick,
            enabled = canMoveNext,
        )

        QuizRecordingStatusText(
            recordingStatus = recordingStatus,
            answerAttemptCount = answer?.attemptCount,
            recognizedText = answer?.sttText,
        )
    }
}

private const val MOCK_SUCCESS_STAR = 3
private const val PERCENT_MAX = 100
private const val FIRST_LETTER_COUNT = 1

private fun submitSttAnswer(
    state: QuizSessionState,
    sttText: String,
): QuizSessionState {
    val question = state.currentQuestion ?: return state
    val submitState =
        if (state.answers.any { it.questionId == question.id }) {
            QuizSessionReducer.retryCurrentQuestion(state)
        } else {
            state
        }

    return QuizSessionReducer.submitCurrentAnswer(
        state = submitState,
        sttText = sttText,
        star = MOCK_SUCCESS_STAR,
    )
}
