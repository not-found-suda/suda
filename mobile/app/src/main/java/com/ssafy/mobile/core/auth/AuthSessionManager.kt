package com.ssafy.mobile.core.auth

import com.ssafy.mobile.core.session.ActiveChildStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionManager
    @Inject
    constructor(
        private val tokenStorage: TokenStorage,
        private val activeChildStorage: ActiveChildStorage,
    ) {
        suspend fun restoreSession(): AuthState {
            val accessToken = tokenStorage.getAccessToken()

            if (accessToken.isNullOrEmpty()) {
                return AuthState.Unauthenticated
            }

            val childId = activeChildStorage.getActiveChildId()

            return if (childId != null) {
                AuthState.AuthenticatedWithChild(childId)
            } else {
                AuthState.AuthenticatedWithoutChild
            }
        }

        suspend fun clearSession() {
            tokenStorage.clearTokens()
            activeChildStorage.clearActiveChildId()
        }
    }
