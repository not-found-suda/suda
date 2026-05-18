package com.ssafy.mobile.feature.login.presentation

import com.ssafy.mobile.core.ui.components.AuthMessageType

sealed interface LoginUiState {
    data object Idle : LoginUiState

    data object Loading : LoginUiState

    data class Success(
        val hasActiveChild: Boolean,
    ) : LoginUiState

    data class Error(
        val type: AuthMessageType = AuthMessageType.General,
    ) : LoginUiState
}
