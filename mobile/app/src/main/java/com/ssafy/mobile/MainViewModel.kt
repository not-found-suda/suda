package com.ssafy.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.auth.AuthSessionManager
import com.ssafy.mobile.core.auth.AuthState
import com.ssafy.mobile.core.ui.theme.AppThemeMode
import com.ssafy.mobile.core.ui.theme.AppThemeModeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        authSessionManager: AuthSessionManager,
        appThemeModeRepository: AppThemeModeRepository,
    ) : ViewModel() {
        val authState: StateFlow<AuthState> = authSessionManager.authState

        val appThemeMode: StateFlow<AppThemeMode> =
            appThemeModeRepository.themeMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MS),
                initialValue = AppThemeMode.System,
            )
    }

private const val STATE_SUBSCRIPTION_TIMEOUT_MS = 5_000L
