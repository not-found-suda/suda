package com.ssafy.mobile.feature.mypage.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.ui.theme.AppThemeMode
import com.ssafy.mobile.core.ui.theme.AppThemeModeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppSettingsViewModel
    @Inject
    constructor(
        private val appThemeModeRepository: AppThemeModeRepository,
    ) : ViewModel() {
        val themeMode: StateFlow<AppThemeMode> =
            appThemeModeRepository.themeMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MS),
                initialValue = AppThemeMode.System,
            )

        fun updateThemeMode(mode: AppThemeMode) {
            viewModelScope.launch {
                appThemeModeRepository.saveThemeMode(mode)
            }
        }
    }

private const val STATE_SUBSCRIPTION_TIMEOUT_MS = 5_000L
