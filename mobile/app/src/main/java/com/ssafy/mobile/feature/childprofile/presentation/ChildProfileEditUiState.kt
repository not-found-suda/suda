package com.ssafy.mobile.feature.childprofile.presentation

sealed interface ChildProfileEditUiState {
    data object Idle : ChildProfileEditUiState

    data object Saving : ChildProfileEditUiState

    data object Success : ChildProfileEditUiState

    data class Error(
        val message: String,
    ) : ChildProfileEditUiState
}
