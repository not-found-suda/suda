package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.learning.domain.repository.LearningQuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class QuizResultViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val quizRepository: LearningQuizRepository,
    ) : ViewModel() {
        private val sessionId: Long =
            checkNotNull(savedStateHandle["sessionId"]) {
                "QuizResult route requires sessionId."
            }

        private val _uiState = MutableStateFlow<QuizResultUiState>(QuizResultUiState.Loading)
        val uiState: StateFlow<QuizResultUiState> = _uiState.asStateFlow()

        private var fetchJob: Job? = null

        init {
            loadQuizResult()
        }

        fun loadQuizResult() {
            if (fetchJob?.isActive == true) return

            fetchJob =
                viewModelScope.launch {
                    _uiState.value = QuizResultUiState.Loading
                    val result =
                        withContext(Dispatchers.IO) {
                            quizRepository.getResult(sessionId)
                        }

                    result
                        .onSuccess { quizResult ->
                            _uiState.value = QuizResultUiState.Success(quizResult)
                        }.onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            _uiState.value =
                                QuizResultUiState.Error(
                                    mapThrowableToMessage(throwable),
                                )
                        }
                }
        }

        private fun mapThrowableToMessage(throwable: Throwable): String {
            val message = throwable.message ?: ""
            return when {
                message.contains("401") || message.contains("로그인이 필요합니다") ->
                    "세션이 만료되었습니다. 다시 로그인해 주세요."

                message.contains("403") || message.contains("퀴즈 요청 권한이 없습니다") ->
                    "결과를 조회할 권한이 없습니다."

                message.contains("404") || message.contains("퀴즈 세션 또는 문제를 찾을 수 없습니다") ->
                    "퀴즈 세션을 찾을 수 없습니다."

                message.contains("409") ||
                    message.contains("LEARN_QUIZ_NOT_COMPLETED") ||
                    message.contains("현재 퀴즈 상태에서는 요청을 처리할 수 없습니다")
                -> "퀴즈가 아직 완료되지 않았어요."

                else -> "퀴즈 결과를 불러오지 못했습니다."
            }
        }
    }
