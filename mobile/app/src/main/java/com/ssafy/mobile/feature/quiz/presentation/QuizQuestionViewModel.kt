package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.feature.learning.data.repository.LearningQuizAnswerSubmissionQueueSyncer
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_QUIZ_QUESTION_COUNT
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerSubmissionSyncEvent
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizQuestion
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizRepository
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer
import com.ssafy.mobile.feature.quiz.domain.model.QuizQuestion
import com.ssafy.mobile.feature.quiz.domain.model.QuizSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
@Suppress("TooManyFunctions")
class QuizQuestionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val quizRepository: LearningQuizRepository,
        private val queueSyncer: LearningQuizAnswerSubmissionQueueSyncer,
        private val activeChildStorage: ActiveChildStorage,
    ) : ViewModel() {
        val categoryId: Long =
            checkNotNull(savedStateHandle["categoryId"]) {
                "Quiz route requires categoryId."
            }
        val difficulty: String = savedStateHandle["difficulty"] ?: DEFAULT_LEARNING_DIFFICULTY
        private var isCompletionPending = false

        private val _quizState = MutableStateFlow(QuizSessionState(isLoading = true))
        val quizState: StateFlow<QuizSessionState> = _quizState.asStateFlow()

        private val _answerSubmitState =
            MutableStateFlow<QuizAnswerSubmitState>(QuizAnswerSubmitState.Idle)
        val answerSubmitState: StateFlow<QuizAnswerSubmitState> = _answerSubmitState.asStateFlow()

        init {
            viewModelScope.launch {
                queueSyncer.events.collect { event ->
                    handleAnswerSubmissionSyncEvent(event)
                }
            }
            startSession()
        }

        fun restart() {
            _answerSubmitState.value = QuizAnswerSubmitState.Idle
            isCompletionPending = false
            startSession()
        }

        fun retry() {
            _answerSubmitState.value = QuizAnswerSubmitState.Idle
            val sessionId = _quizState.value.sessionId
            if (sessionId == null) {
                restart()
            } else {
                loadCurrentQuestion(sessionId)
            }
        }

        fun submitRecognizedText(recognizedText: String) {
            val state = _quizState.value
            val sessionId = state.sessionId
            val question = state.currentQuestion
            val trimmedText = recognizedText.trim()
            when {
                _answerSubmitState.value == QuizAnswerSubmitState.Submitting -> Unit
                isCompletionPending -> Unit
                sessionId == null -> Unit
                question == null -> Unit
                trimmedText.isBlank() -> Unit
                question.word.isBlank() -> {
                    _answerSubmitState.value =
                        QuizAnswerSubmitState.Error("문제 단어 정보를 확인할 수 없습니다.")
                }
                else -> {
                    val previousAnswer =
                        state.answers.firstOrNull { it.questionId == question.id }
                    val answer =
                        QuizAnswer(
                            questionId = question.id,
                            sttText = trimmedText,
                            attemptCount = (previousAnswer?.attemptCount ?: 0) + 1,
                        )

                    _quizState.value =
                        state.copy(
                            answers = state.answers.replaceAnswer(answer),
                            retryCount = (answer.attemptCount - 1).coerceAtLeast(0),
                        )
                    submitAnswerToServer(
                        sessionId = sessionId,
                        question = question,
                        answer = answer,
                    )
                }
            }
        }

        fun moveToNextQuestion() {
            if (_answerSubmitState.value != QuizAnswerSubmitState.Submitting) {
                val sessionId = _quizState.value.sessionId
                val state = _quizState.value
                val question = state.currentQuestion
                val answer =
                    question?.let { currentQuestion ->
                        state.answers.firstOrNull { it.questionId == currentQuestion.id }
                    }

                when {
                    sessionId == null -> Unit
                    isCompletionPending -> {
                        viewModelScope.launch {
                            _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                            completeSession(sessionId)
                        }
                    }
                    question == null -> Unit
                    answer == null -> {
                        _answerSubmitState.value =
                            QuizAnswerSubmitState.Error("답변을 먼저 저장해주세요.")
                    }
                    _answerSubmitState.value is QuizAnswerSubmitState.SaveFailed -> {
                        submitAnswerToServer(
                            sessionId = sessionId,
                            question = question,
                            answer = answer,
                        )
                    }
                    answer.isScored.not() -> Unit
                    else -> {
                        if (state.isLastQuestion()) {
                            viewModelScope.launch {
                                _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                                isCompletionPending = true
                                completeSession(sessionId)
                            }
                        } else {
                            _answerSubmitState.value = QuizAnswerSubmitState.Success
                            loadCurrentQuestion(sessionId)
                        }
                    }
                }
            }
        }

        private fun startSession() {
            viewModelScope.launch {
                _quizState.value = QuizSessionState(isLoading = true)

                val activeChildId =
                    withContext(Dispatchers.IO) {
                        activeChildStorage.getActiveChildId()
                    }
                if (activeChildId == null) {
                    _quizState.value =
                        QuizSessionState(
                            errorMessage = "퀴즈를 시작하려면 아이를 먼저 선택해 주세요.",
                        )
                    return@launch
                }

                val sessionResult =
                    withContext(Dispatchers.IO) {
                        quizRepository.createSession(
                            childProfileId = activeChildId,
                            categoryId = categoryId,
                            difficulty = difficulty,
                            totalQuestionCount = DEFAULT_QUIZ_QUESTION_COUNT,
                        )
                    }

                sessionResult
                    .onSuccess { session ->
                        _quizState.value =
                            QuizSessionState(
                                isLoading = true,
                                sessionId = session.sessionId,
                                totalQuestionCountOverride = session.totalQuestionCount,
                                currentQuestionNumberOverride =
                                    session.currentQuestionNumber,
                            )
                        loadCurrentQuestion(session.sessionId)
                    }.onFailure { throwable ->
                        _quizState.value =
                            QuizSessionState(
                                errorMessage =
                                    throwable.message ?: "퀴즈를 시작하지 못했습니다.",
                            )
                    }
            }
        }

        private fun loadCurrentQuestion(sessionId: Long) {
            viewModelScope.launch {
                _quizState.value = _quizState.value.copy(isLoading = true, errorMessage = null)
                val questionResult =
                    withContext(Dispatchers.IO) {
                        quizRepository.getCurrentQuestion(sessionId)
                    }

                questionResult
                    .onSuccess { question ->
                        applyCurrentQuestion(question)
                    }.onFailure { throwable ->
                        _quizState.value =
                            _quizState.value.copy(
                                isLoading = false,
                                errorMessage =
                                    throwable.message ?: "퀴즈 문제를 불러오지 못했습니다.",
                            )
                    }
            }
        }

        private fun applyCurrentQuestion(question: LearningQuizQuestion) {
            val targetWord =
                question.targetText
                    ?.takeIf { it.isNotBlank() }
            if (targetWord.isNullOrBlank()) {
                _quizState.value =
                    _quizState.value.copy(
                        isLoading = false,
                        errorMessage = "퀴즈 문제 단어 정보를 불러오지 못했습니다.",
                        sessionId = question.sessionId,
                    )
                _answerSubmitState.value =
                    QuizAnswerSubmitState.Error(
                        "퀴즈 문제 정보를 확인할 수 없습니다. 다시 시도해 주세요.",
                    )
            } else {
                val currentQuestion =
                    QuizQuestion(
                        id = question.questionId,
                        wordId = question.wordId,
                        categoryId = categoryId,
                        word = targetWord,
                        imageUrl = question.imageUrl,
                    )
                val questions =
                    _quizState.value.questions
                        .filterNot { it.id == currentQuestion.id } + currentQuestion

                _quizState.value =
                    _quizState.value.copy(
                        questions = questions,
                        currentQuestionIndex = questions.lastIndex,
                        isLoading = false,
                        isFinished = false,
                        errorMessage = null,
                        sessionId = question.sessionId,
                        totalQuestionCountOverride = question.totalQuestionCount,
                        currentQuestionNumberOverride = question.questionNumber,
                    )
                isCompletionPending = false
                _answerSubmitState.value = QuizAnswerSubmitState.Idle
            }
        }

        private fun applyAnswerResult(result: LearningQuizAnswerResult) {
            val state = _quizState.value
            val currentQuestion = state.currentQuestion
            val updatedQuestions =
                if (currentQuestion == null) {
                    state.questions
                } else {
                    state.questions.map { question ->
                        if (question.id == result.questionId) {
                            question.copy(word = result.targetText)
                        } else {
                            question
                        }
                    }
                }
            val previousAnswer =
                state.answers.firstOrNull { answer ->
                    answer.questionId == result.questionId
                }
            val answer =
                QuizAnswer(
                    questionId = result.questionId,
                    sttText = result.recognizedText,
                    star = result.star,
                    attemptCount = previousAnswer?.attemptCount ?: 1,
                    isCorrect = result.isCorrect,
                    feedback = result.feedback,
                )

            _quizState.value =
                state.copy(
                    questions = updatedQuestions,
                    answers = state.answers.replaceAnswer(answer),
                    retryCount = (answer.attemptCount - 1).coerceAtLeast(0),
                )
        }

        private fun submitAnswerToServer(
            sessionId: Long,
            question: QuizQuestion,
            answer: QuizAnswer,
        ) {
            if (_answerSubmitState.value == QuizAnswerSubmitState.Submitting) return

            _answerSubmitState.value = QuizAnswerSubmitState.Submitting
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        quizRepository.submitAnswer(
                            sessionId = sessionId,
                            questionId = question.id,
                            wordId = question.wordId,
                            recognizedText = answer.sttText,
                        )
                    }

                result
                    .onSuccess { answerResult ->
                        applyAnswerResult(answerResult)
                        _answerSubmitState.value = QuizAnswerSubmitState.Success
                    }.onFailure { throwable ->
                        _answerSubmitState.value =
                            QuizAnswerSubmitState.SaveFailed(
                                throwable.message ?: "답변 저장에 실패했습니다. 다시 시도해주세요.",
                            )
                    }
            }
        }

        private suspend fun completeSession(sessionId: Long) {
            val result =
                withContext(Dispatchers.IO) {
                    quizRepository.completeSession(sessionId)
                }

            result
                .onSuccess {
                    isCompletionPending = false
                    _quizState.value = _quizState.value.copy(isFinished = true)
                    _answerSubmitState.value = QuizAnswerSubmitState.Success
                }.onFailure { throwable ->
                    isCompletionPending = true
                    _answerSubmitState.value =
                        QuizAnswerSubmitState.CompletionPending(
                            throwable.message ?: "퀴즈 종료 처리에 실패했습니다. 다시 시도해주세요.",
                        )
                }
        }

        private fun handleAnswerSubmissionSyncEvent(event: LearningQuizAnswerSubmissionSyncEvent) {
            val state = _quizState.value
            val question = state.currentQuestion
            when (event) {
                is LearningQuizAnswerSubmissionSyncEvent.AnswerSynced -> {
                    val result = event.result
                    val isCurrentFailedAnswer =
                        _answerSubmitState.value is QuizAnswerSubmitState.SaveFailed &&
                            state.sessionId == result.sessionId &&
                            question?.id == result.questionId

                    if (isCurrentFailedAnswer) {
                        applyAnswerResult(result)
                        if (result.hasNext) {
                            _answerSubmitState.value = QuizAnswerSubmitState.Success
                            loadCurrentQuestion(result.sessionId)
                        } else {
                            isCompletionPending = true
                            viewModelScope.launch {
                                _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                                completeSession(result.sessionId)
                            }
                        }
                    }
                }
                is LearningQuizAnswerSubmissionSyncEvent.AnswerAcceptedWithoutResult -> {
                    val isCurrentFailedAnswer =
                        _answerSubmitState.value is QuizAnswerSubmitState.SaveFailed &&
                            state.sessionId == event.sessionId &&
                            question?.id == event.questionId

                    if (isCurrentFailedAnswer) {
                        _answerSubmitState.value = QuizAnswerSubmitState.Success
                        if (state.isLastQuestion()) {
                            isCompletionPending = true
                            viewModelScope.launch {
                                _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                                completeSession(event.sessionId)
                            }
                        } else {
                            loadCurrentQuestion(event.sessionId)
                        }
                    }
                }
            }
        }
    }

private fun List<QuizAnswer>.replaceAnswer(answer: QuizAnswer): List<QuizAnswer> =
    filterNot { it.questionId == answer.questionId } + answer

private fun QuizSessionState.isLastQuestion(): Boolean =
    currentQuestionNumberOverride != null &&
        totalQuestionCountOverride != null &&
        currentQuestionNumberOverride >= totalQuestionCountOverride
