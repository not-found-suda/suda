package com.ssafy.mobile.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportSummary
import com.ssafy.mobile.feature.report.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportHomeUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val summaryState: ReportSummaryUiState = ReportSummaryUiState.Idle,
)

sealed interface ReportSummaryUiState {
    data object Idle : ReportSummaryUiState

    data object Loading : ReportSummaryUiState

    data object Empty : ReportSummaryUiState

    data class Success(
        val summary: ReportSummary,
    ) : ReportSummaryUiState

    data class Error(
        val message: String,
    ) : ReportSummaryUiState
}

@HiltViewModel
class ReportHomeViewModel
    @Inject
    constructor(
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val reportRepository: ReportRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReportHomeUiState())
        val uiState: StateFlow<ReportHomeUiState> = _uiState.asStateFlow()
        private var loadJob: Job? = null

        init {
            loadActiveChildProfile()
        }

        fun loadActiveChildProfile() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = ActiveChildProfileState.Loading,
                            summaryState = ReportSummaryUiState.Idle,
                        )
                    val state =
                        withContext(Dispatchers.IO) {
                            activeChildProfileManager.getActiveChildProfile()
                        }
                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = state,
                            summaryState =
                                if (state is ActiveChildProfileState.Selected) {
                                    ReportSummaryUiState.Loading
                                } else {
                                    ReportSummaryUiState.Idle
                                },
                        )

                    if (state is ActiveChildProfileState.Selected) {
                        loadReportSummary(state.profile.childId)
                    }
                }
        }

        private suspend fun loadReportSummary(childId: Long) {
            val result =
                withContext(Dispatchers.IO) {
                    reportRepository.getSummary(childId)
                }

            val currentActiveChildState = _uiState.value.activeChildState
            if (
                currentActiveChildState !is ActiveChildProfileState.Selected ||
                currentActiveChildState.profile.childId != childId
            ) {
                return
            }

            _uiState.value =
                result.fold(
                    onSuccess = { summary ->
                        _uiState.value.copy(
                            summaryState =
                                if (summary.hasCompletedRecords) {
                                    ReportSummaryUiState.Success(summary)
                                } else {
                                    ReportSummaryUiState.Empty
                                },
                        )
                    },
                    onFailure = { throwable ->
                        _uiState.value.copy(
                            summaryState =
                                ReportSummaryUiState.Error(
                                    throwable.message ?: "리포트 요약을 불러오지 못했습니다.",
                                ),
                        )
                    },
                )
        }
    }
