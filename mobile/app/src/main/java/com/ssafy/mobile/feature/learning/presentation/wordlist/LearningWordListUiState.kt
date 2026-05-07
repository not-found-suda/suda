package com.ssafy.mobile.feature.learning.presentation.wordlist

import com.ssafy.mobile.feature.learning.domain.model.LearningWord

sealed interface LearningWordListUiState {
    data object Loading : LearningWordListUiState

    data class Success(
        val words: List<LearningWord>,
    ) : LearningWordListUiState

    data object Empty : LearningWordListUiState

    data class Error(
        val message: String,
    ) : LearningWordListUiState
}
