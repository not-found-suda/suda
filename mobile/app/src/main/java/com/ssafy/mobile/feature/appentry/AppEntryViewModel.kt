package com.ssafy.mobile.feature.appentry

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.auth.AuthSessionManager
import com.ssafy.mobile.core.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class AppEntryViewModel
    @Inject
    constructor(
        private val authSessionManager: AuthSessionManager,
    ) : ViewModel() {
        companion object {
            private const val TAG = "AppEntryViewModel"
            private const val SESSION_RESTORE_TIMEOUT_MS = 3000L
        }

        private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)
        val authState: StateFlow<AuthState> = _authState.asStateFlow()

        init {
            restoreSession()
        }

        fun retryRestoreSession() {
            restoreSession()
        }

        private fun restoreSession() {
            _authState.value = AuthState.Restoring

            viewModelScope.launch {
                val result =
                    runCatching {
                        withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) {
                            withContext(Dispatchers.IO) {
                                authSessionManager.restoreSession()
                            }
                        } ?: AuthState.RestoreFailed
                    }.getOrElse { throwable ->
                        if (throwable is CancellationException) throw throwable

                        Log.e(TAG, "Session restore failed")
                        AuthState.RestoreFailed
                    }

                // timeout 또는 예외 발생 시에도 토큰/childId를 삭제하지 않음
                _authState.value = result
            }
        }
    }
