package com.ssafy.mobile.feature.childprofile.presentation

sealed interface ChildProfileEditUiState {
    data object Idle : ChildProfileEditUiState

    data object Loading : ChildProfileEditUiState

    data object Saving : ChildProfileEditUiState

    data object Success : ChildProfileEditUiState

    data object Deleted : ChildProfileEditUiState

    data class Error(
        val message: String,
    ) : ChildProfileEditUiState
}
