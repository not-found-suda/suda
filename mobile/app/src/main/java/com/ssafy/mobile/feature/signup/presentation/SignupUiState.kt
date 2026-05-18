package com.ssafy.mobile.feature.signup.presentation

import com.ssafy.mobile.core.ui.components.AuthMessageType

sealed interface SignupUiState {
    data object Idle : SignupUiState

    data object Loading : SignupUiState

    data object Success : SignupUiState

    data class Error(
        val type: AuthMessageType = AuthMessageType.General,
    ) : SignupUiState
}
