package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_QUIZ_QUESTION_COUNT
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizQuestion
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizRepository
import com.ssafy.mobile.feature.learning.domain.repository.LearningWordRepository
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer
import com.ssafy.mobile.feature.quiz.domain.model.QuizQuestion
import com.ssafy.mobile.feature.quiz.domain.model.QuizSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
        private val wordRepository: LearningWordRepository,
        private val activeChildStorage: ActiveChildStorage,
    ) : ViewModel() {
        val categoryId: Long =
            checkNotNull(savedStateHandle["categoryId"]) {
                "Quiz route requires categoryId."
            }
        val difficulty: String = savedStateHandle["difficulty"] ?: DEFAULT_LEARNING_DIFFICULTY
        private val resumeSessionId: Long? =
            savedStateHandle
                .get<Long>("sessionId")
                ?.takeUnless { it == RESUME_SESSION_ID_NONE }
        private var isCompletionPending = false

        private val _quizState = MutableStateFlow(QuizSessionState(isLoading = true))
        val quizState: StateFlow<QuizSessionState> = _quizState.asStateFlow()

        private val _answerSubmitState =
            MutableStateFlow<QuizAnswerSubmitState>(QuizAnswerSubmitState.Idle)
        val answerSubmitState: StateFlow<QuizAnswerSubmitState> = _answerSubmitState.asStateFlow()

        init {
            if (resumeSessionId == null) {
                startSession()
            } else {
                resumeSession(resumeSessionId)
            }
        }

        fun restart() {
            deletePendingAudioFiles()
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

        fun submitRecordedAudio(
            audioFile: File,
            audioMimeType: String,
        ) {
            val state = _quizState.value
            val sessionId = state.sessionId
            val question = state.currentQuestion
            when {
                _answerSubmitState.value == QuizAnswerSubmitState.Submitting -> Unit
                isCompletionPending -> Unit
                sessionId == null -> Unit
                question == null -> Unit
                audioFile.exists().not() -> {
                    _answerSubmitState.value =
                        QuizAnswerSubmitState.Error("녹음 파일을 확인할 수 없습니다. 다시 시도해 주세요.")
                }
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
                            sttText = "",
                            attemptCount = (previousAnswer?.attemptCount ?: 0) + 1,
                            audioFile = audioFile,
                            audioMimeType = audioMimeType,
                        )

                    previousAnswer
                        ?.audioFile
                        ?.takeIf { it != audioFile }
                        ?.delete()
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

        override fun onCleared() {
            deletePendingAudioFiles()
            super.onCleared()
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

                val preloadImageUrls =
                    loadPreloadImageUrls()

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
                                preloadImageUrls =
                                    (session.imageUrls + preloadImageUrls)
                                        .filter { it.isNotBlank() }
                                        .distinct(),
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

        private fun resumeSession(sessionId: Long) {
            viewModelScope.launch {
                _quizState.value = QuizSessionState(isLoading = true, sessionId = sessionId)
                _answerSubmitState.value = QuizAnswerSubmitState.Idle
                isCompletionPending = false

                val preloadImageUrls = loadPreloadImageUrls()
                _quizState.value =
                    _quizState.value.copy(
                        preloadImageUrls = preloadImageUrls,
                    )
                loadCurrentQuestion(sessionId)
            }
        }

        private suspend fun loadPreloadImageUrls(): List<String> =
            withContext(Dispatchers.IO) {
                wordRepository
                    .getWords(
                        categoryId = categoryId,
                        difficulty = difficulty,
                    ).getOrNull()
                    .orEmpty()
                    .mapNotNull { word -> word.imageUrl?.takeIf { it.isNotBlank() } }
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
                        preloadImageUrls =
                            (_quizState.value.preloadImageUrls + question.imageUrl)
                                .filter { it.isNotBlank() }
                                .distinct(),
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

            val audioFile = answer.audioFile
            val audioMimeType = answer.audioMimeType
            if (audioFile == null || audioMimeType == null || audioFile.exists().not()) {
                _answerSubmitState.value =
                    QuizAnswerSubmitState.SaveFailed(
                        "녹음 파일을 확인할 수 없습니다. 다시 말해 주세요.",
                    )
                return
            }

            _answerSubmitState.value = QuizAnswerSubmitState.Submitting
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        quizRepository.submitAnswer(
                            sessionId = sessionId,
                            questionId = question.id,
                            audioFile = audioFile,
                            audioMimeType = audioMimeType,
                        )
                    }

                result
                    .onSuccess { answerResult ->
                        audioFile.delete()
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

        private fun deletePendingAudioFiles() {
            _quizState.value.answers.forEach { answer ->
                answer.audioFile?.delete()
            }
        }
    }

private fun List<QuizAnswer>.replaceAnswer(answer: QuizAnswer): List<QuizAnswer> =
    filterNot { it.questionId == answer.questionId } + answer

private fun QuizSessionState.isLastQuestion(): Boolean =
    currentQuestionNumberOverride != null &&
        totalQuestionCountOverride != null &&
        currentQuestionNumberOverride >= totalQuestionCountOverride

private const val RESUME_SESSION_ID_NONE = -1L
