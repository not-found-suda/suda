package com.ssafy.mobile.feature.login.presentation

sealed interface LoginUiState {
    data object Idle : LoginUiState

    data object Loading : LoginUiState

    data class Success(
        val hasActiveChild: Boolean,
    ) : LoginUiState

    data class Error(
        val message: String,
    ) : LoginUiState
}
