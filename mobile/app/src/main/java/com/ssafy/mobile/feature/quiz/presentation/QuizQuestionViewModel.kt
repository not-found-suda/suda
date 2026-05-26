@file:Suppress("TooManyFunctions")

package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.session.ActiveChildStorage
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_QUIZ_QUESTION_COUNT
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizAnswerResult
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizQuestion
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSession
import com.ssafy.mobile.feature.learning.domain.model.LearningQuizSessionQuestion
import com.ssafy.mobile.feature.learning.domain.model.LearningWord
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizRepository
import com.ssafy.mobile.feature.learning.domain.repository.LearningWordRepository
import com.ssafy.mobile.feature.quiz.domain.model.QuizAnswer
import com.ssafy.mobile.feature.quiz.domain.model.QuizQuestion
import com.ssafy.mobile.feature.quiz.domain.model.QuizSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
@Suppress("LargeClass", "TooManyFunctions")
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

        @Suppress("ReturnCount")
        fun submitFallbackIncorrectAnswer(recordingStatus: QuizRecordingStatus) {
            if (
                recordingStatus != QuizRecordingStatus.NoSpeech &&
                recordingStatus != QuizRecordingStatus.Timeout
            ) {
                return
            }
            if (
                _answerSubmitState.value == QuizAnswerSubmitState.Submitting ||
                isCompletionPending
            ) {
                return
            }

            val state = _quizState.value
            val sessionId = state.sessionId ?: return
            val question = state.currentQuestion ?: return
            val previousAnswer =
                state.answers.firstOrNull { answer ->
                    answer.questionId == question.id
                }
            if (previousAnswer?.isScored == true) return

            val fallbackAnswer =
                QuizAnswer(
                    questionId = question.id,
                    sttText = "",
                    attemptCount = (previousAnswer?.attemptCount ?: 0) + 1,
                    audioFile = null,
                    audioMimeType = null,
                )

            previousAnswer
                ?.audioFile
                ?.delete()
            _quizState.value =
                state.copy(
                    answers = state.answers.replaceAnswer(fallbackAnswer),
                    retryCount = (fallbackAnswer.attemptCount - 1).coerceAtLeast(0),
                )
            submitAnswerToServer(
                sessionId = sessionId,
                question = question,
                answer = fallbackAnswer,
                timeoutMessage = recordingStatus.toTimeoutMessage(),
                timeoutFeedback = recordingStatus.toTimeoutFeedback(),
            )
        }

        @Suppress("ReturnCount")
        fun handleSubmitFailureState() {
            val state = _quizState.value
            val sessionId = state.sessionId ?: return
            val question = state.currentQuestion ?: return
            val answer =
                state.answers.firstOrNull { currentAnswer ->
                    currentAnswer.questionId == question.id
                } ?: return

            if (_answerSubmitState.value !is QuizAnswerSubmitState.SaveFailed) {
                return
            }

            transitionToFailedAnswer(
                answer = answer,
                feedback = QUIZ_SUBMIT_FAILED_FEEDBACK,
            )
            viewModelScope.launch {
                submitFallbackIncorrectAnswerToServer(
                    sessionId = sessionId,
                    question = question,
                )
            }
        }

        fun moveToNextQuestion() {
            if (_answerSubmitState.value != QuizAnswerSubmitState.Submitting) {
                val state = _quizState.value
                if (state.isLoading) return

                val sessionId = state.sessionId
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
                        if (state.shouldCompleteAfter(answer)) {
                            viewModelScope.launch {
                                _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                                isCompletionPending = true
                                completeSession(sessionId)
                            }
                        } else {
                            _answerSubmitState.value = QuizAnswerSubmitState.Success
                            advanceToNextQuestion(
                                sessionId = sessionId,
                                currentQuestionId = question.id,
                                nextQuestionNumber =
                                    answer.nextQuestionNumber
                                        ?: state.localNextQuestionNumber(question.id)
                                        ?: state.currentQuestionNumber + 1,
                            )
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

                val categoryWords = loadCategoryWords()
                val preloadImageUrls = categoryWords.preloadImageUrls()

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
                        if (!applyPrefetchedSession(session, categoryWords, preloadImageUrls)) {
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
                        }
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

                val preloadImageUrls = loadCategoryWords().preloadImageUrls()
                _quizState.value =
                    _quizState.value.copy(
                        preloadImageUrls = preloadImageUrls,
                    )
                loadCurrentQuestion(sessionId)
            }
        }

        private suspend fun loadCategoryWords(): List<LearningWord> =
            withContext(Dispatchers.IO) {
                wordRepository
                    .getWords(
                        categoryId = categoryId,
                        difficulty = difficulty,
                    ).getOrNull()
                    .orEmpty()
            }

        private fun applyPrefetchedSession(
            session: LearningQuizSession,
            categoryWords: List<LearningWord>,
            preloadImageUrls: List<String>,
        ): Boolean {
            val prefetchedQuestions =
                session.questions.toQuizQuestions(
                    categoryId = categoryId,
                    categoryWords = categoryWords,
                )
            if (prefetchedQuestions.isEmpty()) {
                return false
            }

            val initialQuestionIndex =
                session.currentQuestionNumber.toPrefetchedIndex(prefetchedQuestions.lastIndex)

            _quizState.value =
                QuizSessionState(
                    questions = prefetchedQuestions,
                    currentQuestionIndex = initialQuestionIndex,
                    isLoading = false,
                    sessionId = session.sessionId,
                    totalQuestionCountOverride =
                        maxOf(session.totalQuestionCount, prefetchedQuestions.size),
                    currentQuestionNumberOverride = initialQuestionIndex + 1,
                    preloadImageUrls =
                        (
                            session.imageUrls +
                                preloadImageUrls +
                                prefetchedQuestions.mapNotNull { quizQuestion ->
                                    quizQuestion.imageUrl?.takeIf { it.isNotBlank() }
                                }
                        ).filter { it.isNotBlank() }
                            .distinct(),
                )
            _answerSubmitState.value = QuizAnswerSubmitState.Idle
            isCompletionPending = false
            return true
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

        private fun loadNextQuestion(
            sessionId: Long,
            previousQuestionId: Long,
        ) {
            viewModelScope.launch {
                _quizState.value = _quizState.value.copy(isLoading = true, errorMessage = null)
                var lastFailure: Throwable? = null

                repeat(QUIZ_NEXT_QUESTION_POLL_ATTEMPTS) { attempt ->
                    val questionResult =
                        withContext(Dispatchers.IO) {
                            quizRepository.getCurrentQuestion(sessionId)
                        }
                    val question = questionResult.getOrNull()

                    if (question != null && question.questionId != previousQuestionId) {
                        applyCurrentQuestion(question)
                        return@launch
                    }

                    lastFailure = questionResult.exceptionOrNull()
                    if (attempt < QUIZ_NEXT_QUESTION_POLL_ATTEMPTS - 1) {
                        delay(QUIZ_NEXT_QUESTION_POLL_DELAY_MS)
                    }
                }

                val message =
                    lastFailure?.message ?: QUIZ_NEXT_QUESTION_LOAD_FAILED_MESSAGE

                _quizState.value =
                    _quizState.value.copy(
                        isLoading = false,
                        errorMessage = message,
                    )
                _answerSubmitState.value = QuizAnswerSubmitState.Error(message)
            }
        }

        private fun advanceToNextQuestion(
            sessionId: Long,
            currentQuestionId: Long,
            nextQuestionNumber: Int?,
        ) {
            val state = _quizState.value
            val prefetchedQuestionIndex =
                state.resolveNextQuestionIndex(
                    currentQuestionId = currentQuestionId,
                    nextQuestionNumber = nextQuestionNumber,
                )
            if (prefetchedQuestionIndex != null) {
                moveToQuestionIndex(prefetchedQuestionIndex)
                return
            }

            if (state.hasCompletePrefetchedQuestionSet()) {
                if (state.shouldCompleteByPrefetchBoundary()) {
                    viewModelScope.launch {
                        _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                        isCompletionPending = true
                        completeSession(sessionId)
                    }
                } else {
                    _quizState.value = state.copy(errorMessage = null, isLoading = false)
                    _answerSubmitState.value = QuizAnswerSubmitState.Idle
                }
                return
            }

            loadNextQuestion(
                sessionId = sessionId,
                previousQuestionId = currentQuestionId,
            )
        }

        private fun moveToQuestionIndex(questionIndex: Int) {
            val state = _quizState.value
            if (questionIndex !in state.questions.indices) {
                return
            }

            _quizState.value =
                state.copy(
                    currentQuestionIndex = questionIndex,
                    currentQuestionNumberOverride = questionIndex + 1,
                    isLoading = false,
                    isFinished = false,
                    errorMessage = null,
                )
            _answerSubmitState.value = QuizAnswerSubmitState.Idle
            isCompletionPending = false
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
                val state = _quizState.value
                val currentQuestion =
                    QuizQuestion(
                        id = question.questionId,
                        wordId = question.wordId,
                        categoryId = categoryId,
                        word = targetWord,
                        imageUrl = question.imageUrl,
                    )
                val existingIndex =
                    state.questions.indexOfFirst { quizQuestion ->
                        quizQuestion.id == currentQuestion.id
                    }
                val questions =
                    if (existingIndex >= 0) {
                        state.questions.map { quizQuestion ->
                            if (quizQuestion.id == currentQuestion.id) {
                                currentQuestion
                            } else {
                                quizQuestion
                            }
                        }
                    } else {
                        state.questions + currentQuestion
                    }
                val currentQuestionIndex =
                    existingIndex.takeIf { it >= 0 } ?: questions.lastIndex

                _quizState.value =
                    state.copy(
                        questions = questions,
                        currentQuestionIndex = currentQuestionIndex,
                        isLoading = false,
                        isFinished = false,
                        errorMessage = null,
                        sessionId = question.sessionId,
                        totalQuestionCountOverride =
                            maxOf(question.totalQuestionCount, questions.size),
                        currentQuestionNumberOverride =
                            question.questionNumber.takeIf { it > 0 }
                                ?: (currentQuestionIndex + 1),
                        preloadImageUrls =
                            (state.preloadImageUrls + question.imageUrl)
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
            val localNextQuestionNumber =
                state.localNextQuestionNumber(result.questionId)
            val updatedQuestions =
                if (currentQuestion == null) {
                    state.questions
                } else {
                    state.questions.map { question ->
                        if (question.id == result.questionId) {
                            result.targetText
                                .takeIf { it.isNotBlank() }
                                ?.let { targetText -> question.copy(word = targetText) }
                                ?: question
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
                    hasNextQuestion = result.hasNext || localNextQuestionNumber != null,
                    nextQuestionNumber = result.nextQuestionNumber ?: localNextQuestionNumber,
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
            timeoutMessage: String = QUIZ_SUBMIT_TIMEOUT_MESSAGE,
            timeoutFeedback: String = QUIZ_SUBMIT_TIMEOUT_FEEDBACK,
        ) {
            if (_answerSubmitState.value == QuizAnswerSubmitState.Submitting) return

            val audioFile = answer.audioFile
            val audioMimeType = answer.audioMimeType
            val hasAudioAttachment = audioFile != null || audioMimeType != null
            val isInvalidAudioAttachment =
                audioFile == null ||
                    audioMimeType == null ||
                    audioFile.exists().not()
            if (
                hasAudioAttachment &&
                isInvalidAudioAttachment
            ) {
                _answerSubmitState.value =
                    QuizAnswerSubmitState.SaveFailed(
                        "녹음 파일을 확인할 수 없습니다. 다시 말해 주세요.",
                    )
                return
            }

            _answerSubmitState.value = QuizAnswerSubmitState.Submitting
            viewModelScope.launch {
                val submission =
                    viewModelScope.async(Dispatchers.IO) {
                        quizRepository.submitAnswer(
                            sessionId = sessionId,
                            questionId = question.id,
                            audioFile = audioFile,
                            audioMimeType = audioMimeType,
                        )
                    }
                val result =
                    withTimeoutOrNull(QUIZ_SUBMIT_TIMEOUT_MS) {
                        submission.await()
                    }

                val submitResult =
                    result ?: run {
                        handleTimedOutSubmission(
                            question = question,
                            answer = answer,
                            audioFile = audioFile,
                            submission = submission,
                            timeoutMessage = timeoutMessage,
                            timeoutFeedback = timeoutFeedback,
                        )
                        return@launch
                    }

                submitResult
                    .onSuccess { answerResult ->
                        audioFile?.delete()
                        applyAnswerResult(answerResult)
                        _answerSubmitState.value = QuizAnswerSubmitState.Success
                    }.onFailure { throwable ->
                        transitionToFailedAnswer(
                            answer = answer,
                            feedback = throwable.message ?: QUIZ_SUBMIT_FAILED_FEEDBACK,
                            message = QUIZ_SUBMIT_FAILED_MESSAGE,
                        )
                        submitFallbackIncorrectAnswerToServer(
                            sessionId = sessionId,
                            question = question,
                        )
                    }
            }
        }

        private suspend fun submitFallbackIncorrectAnswerToServer(
            sessionId: Long,
            question: QuizQuestion,
        ) {
            val fallbackResult =
                withContext(Dispatchers.IO) {
                    quizRepository.submitAnswer(
                        sessionId = sessionId,
                        questionId = question.id,
                        audioFile = null,
                        audioMimeType = null,
                    )
                }

            fallbackResult.onSuccess { answerResult ->
                applyAnswerResult(answerResult)
                val state = _quizState.value
                val isStillShowingFailedQuestion =
                    state.sessionId == sessionId &&
                        state.currentQuestion?.id == question.id &&
                        _answerSubmitState.value is QuizAnswerSubmitState.TimedOut

                if (isStillShowingFailedQuestion) {
                    _answerSubmitState.value =
                        QuizAnswerSubmitState.TimedOut(
                            QUIZ_SUBMIT_FAILED_MESSAGE,
                        )
                }
            }
        }

        private fun handleTimedOutSubmission(
            question: QuizQuestion,
            answer: QuizAnswer,
            audioFile: File?,
            submission: kotlinx.coroutines.Deferred<Result<LearningQuizAnswerResult>>,
            timeoutMessage: String,
            timeoutFeedback: String,
        ) {
            transitionToFailedAnswer(
                answer = answer,
                feedback = timeoutFeedback,
                message = timeoutMessage,
            )

            awaitLateSubmitResult(
                questionId = question.id,
                attemptCount = answer.attemptCount,
                audioFile = audioFile,
                submission = submission,
            )
        }

        private fun transitionToFailedAnswer(
            answer: QuizAnswer,
            feedback: String,
            message: String = QUIZ_SUBMIT_FAILED_MESSAGE,
        ) {
            answer.audioFile?.delete()
            applyTimedOutAnswer(
                answer = answer,
                feedback = feedback,
                nextQuestionNumber = _quizState.value.localNextQuestionNumber(answer.questionId),
            )
            _quizState.value = _quizState.value.copy(errorMessage = null)
            _answerSubmitState.value = QuizAnswerSubmitState.TimedOut(message)
        }

        private fun awaitLateSubmitResult(
            questionId: Long,
            attemptCount: Int,
            audioFile: File?,
            submission: kotlinx.coroutines.Deferred<Result<LearningQuizAnswerResult>>,
        ) {
            viewModelScope.launch {
                val result =
                    runCatching {
                        submission.await()
                    }.getOrElse { throwable ->
                        if (throwable is CancellationException) {
                            throw throwable
                        }
                        Result.failure(throwable)
                    }

                if (!isAwaitingTimedOutAnswer(questionId, attemptCount)) {
                    audioFile?.delete()
                    return@launch
                }

                result
                    .onSuccess { answerResult ->
                        audioFile?.delete()
                        applyAnswerResult(answerResult)
                        _answerSubmitState.value = QuizAnswerSubmitState.Success
                    }.onFailure {
                        audioFile?.delete()
                        _answerSubmitState.value =
                            QuizAnswerSubmitState.TimedOut(QUIZ_LATE_SUBMIT_TIMEOUT_MESSAGE)
                    }
            }
        }

        private fun isAwaitingTimedOutAnswer(
            questionId: Long,
            attemptCount: Int,
        ): Boolean {
            val currentAnswer =
                _quizState.value.answers.firstOrNull { answer ->
                    answer.questionId == questionId
                }
            return currentAnswer?.attemptCount == attemptCount &&
                _answerSubmitState.value is QuizAnswerSubmitState.TimedOut
        }

        private fun applyTimedOutAnswer(
            answer: QuizAnswer,
            feedback: String,
            nextQuestionNumber: Int?,
        ) {
            val timedOutAnswer =
                answer.copy(
                    star = 0,
                    isCorrect = false,
                    hasNextQuestion = nextQuestionNumber != null,
                    nextQuestionNumber = nextQuestionNumber,
                    feedback = feedback,
                )

            _quizState.value =
                _quizState.value.copy(
                    answers = _quizState.value.answers.replaceAnswer(timedOutAnswer),
                    retryCount = (timedOutAnswer.attemptCount - 1).coerceAtLeast(0),
                )
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

private fun QuizRecordingStatus.toTimeoutMessage(): String =
    when (this) {
        QuizRecordingStatus.NoSpeech ->
            "목소리를 듣지 못해 틀린 답으로 처리했어요. 다음 문제를 준비 중이에요."
        QuizRecordingStatus.Timeout ->
            "입력이 없어 틀린 답으로 처리했어요. 다음 문제를 준비 중이에요."
        else -> QUIZ_SUBMIT_TIMEOUT_MESSAGE
    }

private fun QuizRecordingStatus.toTimeoutFeedback(): String =
    when (this) {
        QuizRecordingStatus.NoSpeech -> "목소리를 듣지 못해 오답으로 처리했어요."
        QuizRecordingStatus.Timeout -> "입력이 없어 오답으로 처리했어요."
        else -> QUIZ_SUBMIT_TIMEOUT_FEEDBACK
    }

private fun QuizSessionState.isLastQuestion(): Boolean =
    currentQuestionNumberOverride != null &&
        totalQuestionCountOverride != null &&
        currentQuestionNumberOverride >= totalQuestionCountOverride

private fun QuizSessionState.shouldCompleteAfter(answer: QuizAnswer): Boolean =
    isLastQuestion() ||
        (
            answer.hasNextQuestion == false &&
                localNextQuestionNumber(answer.questionId) == null &&
                hasRemainingQuestionByCount().not()
        )

private fun QuizSessionState.hasRemainingQuestionByCount(): Boolean =
    currentQuestionNumberOverride != null &&
        totalQuestionCountOverride != null &&
        currentQuestionNumberOverride < totalQuestionCountOverride

private fun QuizSessionState.hasCompletePrefetchedQuestionSet(): Boolean =
    questions.isNotEmpty() && questions.size >= totalQuestionCount

private fun QuizSessionState.shouldCompleteByPrefetchBoundary(): Boolean =
    currentQuestionIndex >= questions.lastIndex

private fun QuizSessionState.localNextQuestionNumber(currentQuestionId: Long): Int? {
    val currentIndex =
        questions
            .indexOfFirst { question ->
                question.id == currentQuestionId
            }.takeIf { questionIndex -> questionIndex >= 0 }
            ?: currentQuestionIndex

    val nextIndex = currentIndex + 1
    return (nextIndex + 1).takeIf { nextQuestionNumber ->
        nextIndex in questions.indices &&
            nextQuestionNumber <= totalQuestionCount
    }
}

private fun List<LearningWord>.preloadImageUrls(): List<String> =
    mapNotNull { learningWord ->
        learningWord.imageUrl?.takeIf { it.isNotBlank() }
    }

private fun List<LearningQuizSessionQuestion>.toQuizQuestions(
    categoryId: Long,
    categoryWords: List<LearningWord>,
): List<QuizQuestion> =
    sortedBy { sessionQuestion -> sessionQuestion.questionNumber }
        .mapNotNull { sessionQuestion ->
            val matchedWord = categoryWords.findMatchingWord(sessionQuestion.targetText)
            val targetWord =
                sessionQuestion.targetText.takeIf { it.isNotBlank() }
                    ?: matchedWord?.displayText?.takeIf { it.isNotBlank() }
                    ?: matchedWord?.word?.takeIf { it.isNotBlank() }

            targetWord?.let { word ->
                QuizQuestion(
                    id = sessionQuestion.questionId,
                    wordId = matchedWord?.id ?: UNKNOWN_QUIZ_WORD_ID,
                    categoryId = categoryId,
                    word = word,
                    imageUrl =
                        sessionQuestion.imageUrl?.takeIf { it.isNotBlank() }
                            ?: matchedWord?.imageUrl?.takeIf { it.isNotBlank() },
                )
            }
        }

private fun List<LearningWord>.findMatchingWord(targetText: String): LearningWord? {
    val normalizedTarget = targetText.normalizedQuizText()
    return firstOrNull { learningWord ->
        learningWord.word.normalizedQuizText() == normalizedTarget ||
            learningWord.displayText?.normalizedQuizText() == normalizedTarget
    }
}

private fun String.normalizedQuizText(): String =
    trim()
        .lowercase()
        .filterNot { character -> character.isWhitespace() }

private fun Int.toPrefetchedIndex(lastIndex: Int): Int = (this - 1).coerceIn(0, lastIndex)

@Suppress("ReturnCount")
private fun QuizSessionState.resolveNextQuestionIndex(
    currentQuestionId: Long,
    nextQuestionNumber: Int?,
): Int? {
    val explicitIndex =
        nextQuestionNumber
            ?.takeIf { questionNumber -> questionNumber > 0 }
            ?.minus(1)
            ?.takeIf { questionIndex ->
                questionIndex in questions.indices &&
                    questions[questionIndex].id != currentQuestionId
            }
    if (explicitIndex != null) {
        return explicitIndex
    }

    val currentIndex =
        questions
            .indexOfFirst { question ->
                question.id == currentQuestionId
            }.takeIf { questionIndex -> questionIndex >= 0 }
            ?: currentQuestionIndex

    val sequentialIndex = currentIndex + 1
    return sequentialIndex.takeIf { questionIndex ->
        questionIndex in questions.indices
    }
}

private const val RESUME_SESSION_ID_NONE = -1L
private const val UNKNOWN_QUIZ_WORD_ID = -1L
private const val QUIZ_SUBMIT_TIMEOUT_MS = 5_000L
private const val QUIZ_NEXT_QUESTION_POLL_DELAY_MS = 350L
private const val QUIZ_NEXT_QUESTION_POLL_ATTEMPTS = 18
private const val QUIZ_NEXT_QUESTION_LOAD_FAILED_MESSAGE =
    "다음 문제를 준비하지 못했습니다. 다시 시도해 주세요."
private const val QUIZ_SUBMIT_FAILED_MESSAGE = "실패!"
private const val QUIZ_SUBMIT_FAILED_FEEDBACK = "틀렸어요."
private const val QUIZ_SUBMIT_TIMEOUT_MESSAGE =
    QUIZ_SUBMIT_FAILED_MESSAGE
private const val QUIZ_SUBMIT_TIMEOUT_FEEDBACK = "채점 시간이 길어 오답으로 처리했어요."
private const val QUIZ_LATE_SUBMIT_TIMEOUT_MESSAGE =
    "다음 문제를 준비하는 데 시간이 걸리고 있어요. 잠시만 기다려 주세요."
