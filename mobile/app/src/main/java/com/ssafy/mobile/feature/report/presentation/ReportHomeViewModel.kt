package com.ssafy.mobile.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportHomeUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
)

@HiltViewModel
class ReportHomeViewModel
    @Inject
    constructor(
        private val activeChildProfileManager: ActiveChildProfileManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReportHomeUiState())
        val uiState: StateFlow<ReportHomeUiState> = _uiState.asStateFlow()

        init {
            loadActiveChildProfile()
        }

        fun loadActiveChildProfile() {
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        activeChildState = ActiveChildProfileState.Loading,
                    )
                val state =
                    withContext(Dispatchers.IO) {
                        activeChildProfileManager.getActiveChildProfile()
                    }
                _uiState.value =
                    _uiState.value.copy(
                        activeChildState = state,
                    )
            }
        }
    }
