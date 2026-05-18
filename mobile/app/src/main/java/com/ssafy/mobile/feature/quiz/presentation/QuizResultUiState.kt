package com.ssafy.mobile.feature.quiz.presentation

import com.ssafy.mobile.feature.learning.domain.model.LearningQuizResult

sealed interface QuizResultUiState {
    data object Loading : QuizResultUiState

    data class Success(
        val result: LearningQuizResult,
    ) : QuizResultUiState

    data class Error(
        val message: String,
    ) : QuizResultUiState
}
