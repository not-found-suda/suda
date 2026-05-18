package com.ssafy.mobile.core.auth

import com.ssafy.mobile.core.session.ActiveChildStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthSessionManager
    @Inject
    constructor(
        private val tokenStorage: TokenStorage,
        private val activeChildStorage: ActiveChildStorage,
    ) {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)
        val authState: StateFlow<AuthState> = _authState.asStateFlow()

        suspend fun restoreSession(): AuthState {
            val accessToken = tokenStorage.getAccessToken()

            val result =
                if (accessToken.isNullOrEmpty()) {
                    AuthState.Unauthenticated
                } else {
                    val childId = activeChildStorage.getActiveChildId()

                    if (childId != null) {
                        AuthState.AuthenticatedWithChild(childId)
                    } else {
                        AuthState.AuthenticatedWithoutChild
                    }
                }

            _authState.value = result
            return result
        }

        suspend fun clearSession() {
            tokenStorage.clearTokens()
            activeChildStorage.clearActiveChildId()
            _authState.value = AuthState.Unauthenticated
        }

        fun updateAuthState(state: AuthState) {
            _authState.value = state
        }
    }
