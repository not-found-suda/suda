package com.ssafy.mobile.core.auth

sealed interface AuthState {
    data object Restoring : AuthState

    data object RestoreFailed : AuthState

    data object Unauthenticated : AuthState

    data object AuthenticatedWithoutChild : AuthState

    data class AuthenticatedWithChild(
        val childId: Long,
    ) : AuthState
}
