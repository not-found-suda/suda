package com.ssafy.mobile.feature.learning.presentation.wordlist

import com.ssafy.mobile.feature.learning.domain.model.LearningWord

sealed interface LearningWordListUiState {
    data object Loading : LearningWordListUiState

    data class Success(
        val words: List<LearningWord>,
        val currentIndex: Int = 0,
        val audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    ) : LearningWordListUiState {
        val currentWord: LearningWord? = words.getOrNull(currentIndex)
        val hasNext: Boolean = currentIndex < words.size - 1
        val hasPrevious: Boolean = currentIndex > 0
    }

    data object Empty : LearningWordListUiState

    data class Error(
        val message: String,
    ) : LearningWordListUiState
}

enum class AudioPlaybackState {
    Idle,
    Loading,
    Playing,
    Error,
}
