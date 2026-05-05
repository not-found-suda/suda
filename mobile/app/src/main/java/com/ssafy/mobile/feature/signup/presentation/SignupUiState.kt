package com.ssafy.mobile.feature.signup.presentation

sealed interface SignupUiState {
    data object Idle : SignupUiState

    data object Loading : SignupUiState

    data object Success : SignupUiState

    data class Error(
        val message: String,
    ) : SignupUiState
}
