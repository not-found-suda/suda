package com.ssafy.mobile.feature.quiz.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.audio.WavFileHeader
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
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
        @param:ApplicationContext private val appContext: Context,
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

            val silentAudioFile =
                createSilentAudioFile(question.id) ?: run {
                    _answerSubmitState.value =
                        QuizAnswerSubmitState.Error("무음 답변 파일을 준비하지 못했어요.")
                    return
                }

            val answer =
                QuizAnswer(
                    questionId = question.id,
                    sttText = "",
                    attemptCount = (previousAnswer?.attemptCount ?: 0) + 1,
                    audioFile = silentAudioFile,
                    audioMimeType = QUIZ_AUDIO_MIME_TYPE,
                )

            previousAnswer
                ?.audioFile
                ?.takeIf { it != silentAudioFile }
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
                sessionId = sessionId,
                question = question,
                answer = answer,
                feedback = QUIZ_SUBMIT_FAILED_FEEDBACK,
            )
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
                        if (state.isLastQuestion()) {
                            viewModelScope.launch {
                                _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                                isCompletionPending = true
                                completeSession(sessionId)
                            }
                        } else {
                            _answerSubmitState.value = QuizAnswerSubmitState.Success
                            loadNextQuestion(
                                sessionId = sessionId,
                                previousQuestionId = question.id,
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
            timeoutMessage: String = QUIZ_SUBMIT_TIMEOUT_MESSAGE,
            timeoutFeedback: String = QUIZ_SUBMIT_TIMEOUT_FEEDBACK,
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
                            sessionId = sessionId,
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
                        audioFile.delete()
                        applyAnswerResult(answerResult)
                        _answerSubmitState.value = QuizAnswerSubmitState.Success
                        if (!answerResult.isCorrect) {
                            scheduleAutoAdvanceAfterIncorrectAnswer(
                                sessionId = sessionId,
                                questionId = answerResult.questionId,
                            )
                        }
                    }.onFailure { throwable ->
                        _answerSubmitState.value =
                            QuizAnswerSubmitState.SaveFailed(
                                throwable.message ?: "답변 저장에 실패했습니다. 다시 시도해주세요.",
                            )
                    }
            }
        }

        private fun handleTimedOutSubmission(
            sessionId: Long,
            question: QuizQuestion,
            answer: QuizAnswer,
            audioFile: File,
            submission: kotlinx.coroutines.Deferred<Result<LearningQuizAnswerResult>>,
            timeoutMessage: String,
            timeoutFeedback: String,
        ) {
            transitionToFailedAnswer(
                sessionId = sessionId,
                question = question,
                answer = answer,
                feedback = timeoutFeedback,
                message = timeoutMessage,
            )

            awaitLateSubmitResult(
                sessionId = sessionId,
                questionId = question.id,
                attemptCount = answer.attemptCount,
                audioFile = audioFile,
                submission = submission,
            )
        }

        private fun transitionToFailedAnswer(
            sessionId: Long,
            question: QuizQuestion,
            answer: QuizAnswer,
            feedback: String,
            message: String = QUIZ_SUBMIT_FAILED_MESSAGE,
        ) {
            answer.audioFile?.delete()
            applyTimedOutAnswer(answer, feedback)
            _quizState.value = _quizState.value.copy(errorMessage = null)
            _answerSubmitState.value = QuizAnswerSubmitState.TimedOut(message)

            if (_quizState.value.isLastQuestion().not()) {
                requestNextQuestionAfterTimeout(
                    sessionId = sessionId,
                    timedOutQuestionId = question.id,
                )
            }
        }

        @Suppress("ComplexCondition")
        private fun scheduleAutoAdvanceAfterIncorrectAnswer(
            sessionId: Long,
            questionId: Long,
        ) {
            viewModelScope.launch {
                delay(QUIZ_FAILED_ANSWER_DISPLAY_MS)

                val state = _quizState.value
                val currentAnswer =
                    state.answers.firstOrNull { answer ->
                        answer.questionId == questionId
                    }

                val canAutoAdvance =
                    state.sessionId == sessionId &&
                        !state.isLoading &&
                        state.currentQuestion?.id == questionId &&
                        currentAnswer?.isCorrect == false &&
                        _answerSubmitState.value == QuizAnswerSubmitState.Success
                if (!canAutoAdvance) {
                    return@launch
                }

                if (state.isLastQuestion()) {
                    _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                    isCompletionPending = true
                    completeSession(sessionId)
                } else {
                    loadNextQuestion(
                        sessionId = sessionId,
                        previousQuestionId = questionId,
                    )
                }
            }
        }

        private fun requestNextQuestionAfterTimeout(
            sessionId: Long,
            timedOutQuestionId: Long,
            immediate: Boolean = false,
        ) {
            viewModelScope.launch {
                if (!immediate) {
                    delay(QUIZ_TIMEOUT_FEEDBACK_DELAY_MS)
                }

                if (_answerSubmitState.value !is QuizAnswerSubmitState.TimedOut) {
                    return@launch
                }

                _quizState.value =
                    _quizState.value.copy(
                        errorMessage = null,
                    )

                repeat(QUIZ_NEXT_QUESTION_POLL_ATTEMPTS) {
                    val result =
                        withContext(Dispatchers.IO) {
                            quizRepository.getCurrentQuestion(sessionId)
                        }
                    val nextQuestion = result.getOrNull()
                    if (nextQuestion != null && nextQuestion.questionId != timedOutQuestionId) {
                        applyCurrentQuestion(nextQuestion)
                        return@launch
                    }
                    delay(QUIZ_NEXT_QUESTION_POLL_DELAY_MS)
                }

                if (_answerSubmitState.value is QuizAnswerSubmitState.TimedOut) {
                    _answerSubmitState.value =
                        QuizAnswerSubmitState.TimedOut(QUIZ_NEXT_QUESTION_DELAY_MESSAGE)
                }
            }
        }

        private fun awaitLateSubmitResult(
            sessionId: Long,
            questionId: Long,
            attemptCount: Int,
            audioFile: File,
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
                    audioFile.delete()
                    return@launch
                }

                result
                    .onSuccess { answerResult ->
                        audioFile.delete()
                        applyAnswerResult(answerResult)
                        if (answerResult.hasNext) {
                            requestNextQuestionAfterTimeout(
                                sessionId = sessionId,
                                timedOutQuestionId = questionId,
                                immediate = true,
                            )
                        } else {
                            viewModelScope.launch {
                                _answerSubmitState.value = QuizAnswerSubmitState.Submitting
                                isCompletionPending = true
                                completeSession(sessionId)
                            }
                        }
                    }.onFailure {
                        audioFile.delete()
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
        ) {
            val timedOutAnswer =
                answer.copy(
                    star = 0,
                    isCorrect = false,
                    feedback = feedback,
                )

            _quizState.value =
                _quizState.value.copy(
                    answers = _quizState.value.answers.replaceAnswer(timedOutAnswer),
                    retryCount = (timedOutAnswer.attemptCount - 1).coerceAtLeast(0),
                )
        }

        private fun createSilentAudioFile(questionId: Long): File? =
            runCatching {
                File
                    .createTempFile(
                        "$QUIZ_SILENT_AUDIO_PREFIX$questionId",
                        QUIZ_SILENT_AUDIO_SUFFIX,
                        appContext.cacheDir,
                    ).apply {
                        FileOutputStream(this).use { outputStream ->
                            outputStream.write(
                                WavFileHeader.create(
                                    pcmDataSize = QUIZ_SILENT_PCM_SIZE_BYTES.toLong(),
                                    sampleRate = QUIZ_AUDIO_SAMPLE_RATE,
                                    channelCount = QUIZ_AUDIO_CHANNEL_COUNT,
                                    bitsPerSample = QUIZ_AUDIO_BITS_PER_SAMPLE,
                                ),
                            )
                            outputStream.write(ByteArray(QUIZ_SILENT_PCM_SIZE_BYTES))
                        }
                    }
            }.getOrNull()

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

private const val RESUME_SESSION_ID_NONE = -1L
private const val QUIZ_SUBMIT_TIMEOUT_MS = 5_000L
private const val QUIZ_FAILED_ANSWER_DISPLAY_MS = 700L
private const val QUIZ_TIMEOUT_FEEDBACK_DELAY_MS = 450L
private const val QUIZ_NEXT_QUESTION_POLL_DELAY_MS = 350L
private const val QUIZ_NEXT_QUESTION_POLL_ATTEMPTS = 18
private const val QUIZ_NEXT_QUESTION_LOAD_FAILED_MESSAGE =
    "다음 문제를 준비하지 못했습니다. 다시 시도해 주세요."
private const val QUIZ_AUDIO_SAMPLE_RATE = 16_000
private const val QUIZ_AUDIO_CHANNEL_COUNT = 1
private const val QUIZ_AUDIO_BITS_PER_SAMPLE = 16
private const val QUIZ_SILENT_PCM_SIZE_BYTES = 32_000
private const val QUIZ_AUDIO_MIME_TYPE = "audio/wav"
private const val QUIZ_SILENT_AUDIO_PREFIX = "quiz_silence_"
private const val QUIZ_SILENT_AUDIO_SUFFIX = ".wav"
private const val QUIZ_SUBMIT_FAILED_MESSAGE = "실패!"
private const val QUIZ_SUBMIT_FAILED_FEEDBACK = "틀렸어요."
private const val QUIZ_SUBMIT_TIMEOUT_MESSAGE =
    QUIZ_SUBMIT_FAILED_MESSAGE
private const val QUIZ_SUBMIT_TIMEOUT_FEEDBACK = "채점 시간이 길어 오답으로 처리했어요."
private const val QUIZ_LATE_SUBMIT_TIMEOUT_MESSAGE =
    "다음 문제를 준비하는 데 시간이 걸리고 있어요. 잠시만 기다려 주세요."
private const val QUIZ_NEXT_QUESTION_DELAY_MESSAGE =
    "다음 문제를 아직 준비하지 못했어요. 잠시 후 자동으로 넘어가요."
