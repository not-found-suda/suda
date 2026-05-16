package com.ssafy.mobile.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationAnalysisStatus
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationSummary
import com.ssafy.mobile.feature.report.domain.model.ReportFilterState
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

data class ReportCommunicationSummaryUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val communicationSummaryState: ReportCommunicationSummaryState =
        ReportCommunicationSummaryState.Idle,
    val filterUiState: ReportFilterUiState = ReportFilterUiState(),
)

sealed interface ReportCommunicationSummaryState {
    data object Idle : ReportCommunicationSummaryState

    data object Loading : ReportCommunicationSummaryState

    data class Success(
        val summary: ReportCommunicationSummary,
    ) : ReportCommunicationSummaryState

    data class Error(
        val message: String,
    ) : ReportCommunicationSummaryState
}

@HiltViewModel
class ReportCommunicationSummaryViewModel
    @Inject
    constructor(
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val reportRepository: ReportRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReportCommunicationSummaryUiState())
        val uiState: StateFlow<ReportCommunicationSummaryUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null
        private var appliedFilter = defaultReportFilterState()

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
                            communicationSummaryState = ReportCommunicationSummaryState.Idle,
                        )

                    val state =
                        withContext(Dispatchers.IO) {
                            activeChildProfileManager.getActiveChildProfile()
                        }

                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = state,
                            communicationSummaryState =
                                if (state is ActiveChildProfileState.Selected) {
                                    ReportCommunicationSummaryState.Loading
                                } else {
                                    ReportCommunicationSummaryState.Idle
                                },
                        )

                    if (state is ActiveChildProfileState.Selected) {
                        loadCommunicationSummary(
                            childId = state.profile.childId,
                            filter = appliedFilter,
                        )
                    }
                }
        }

        fun updateFilterInput(input: ReportFilterInputState) {
            _uiState.value =
                _uiState.value.copy(
                    filterUiState =
                        _uiState.value.filterUiState.copy(
                            input = input,
                            errorMessage = null,
                        ),
                )
        }

        fun applyFilter() {
            val filterResult =
                _uiState.value.filterUiState.input
                    .toDateFilter()
            val filter =
                filterResult.getOrElse { throwable ->
                    _uiState.value =
                        _uiState.value.copy(
                            filterUiState =
                                _uiState.value.filterUiState.copy(
                                    errorMessage = throwable.message ?: "필터 값을 확인해 주세요.",
                                ),
                        )
                    return
                }

            appliedFilter = filter
            _uiState.value =
                _uiState.value.copy(
                    filterUiState =
                        _uiState.value.filterUiState.copy(
                            hasAppliedFilter = filter.hasFilters(),
                            errorMessage = null,
                        ),
                )
            reloadCommunicationSummaryWithCurrentChild()
        }

        fun resetFilter() {
            appliedFilter = defaultReportFilterState()
            _uiState.value =
                _uiState.value.copy(
                    filterUiState = ReportFilterUiState(),
                )
            reloadCommunicationSummaryWithCurrentChild()
        }

        private fun reloadCommunicationSummaryWithCurrentChild() {
            val childState =
                _uiState.value.activeChildState as? ActiveChildProfileState.Selected
                    ?: return
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(
                            communicationSummaryState = ReportCommunicationSummaryState.Loading,
                        )
                    loadCommunicationSummary(
                        childId = childState.profile.childId,
                        filter = appliedFilter,
                    )
                }
        }

        private suspend fun loadCommunicationSummary(
            childId: Long,
            filter: ReportFilterState,
        ) {
            val result =
                withContext(Dispatchers.IO) {
                    reportRepository.getCommunicationSummary(
                        childId = childId,
                        filter = filter,
                    )
                }

            if (!isSameActiveChild(childId = childId)) {
                return
            }

            _uiState.value =
                result.fold(
                    onSuccess = { summary ->
                        _uiState.value.copy(
                            communicationSummaryState =
                                ReportCommunicationSummaryState.Success(
                                    summary = summary.withSafeStatus(),
                                ),
                        )
                    },
                    onFailure = { throwable ->
                        _uiState.value.copy(
                            communicationSummaryState =
                                ReportCommunicationSummaryState.Error(
                                    throwable.message ?: "소통 발화 분석 리포트를 불러오지 못했습니다.",
                                ),
                        )
                    },
                )
        }

        private fun ReportCommunicationSummary.withSafeStatus(): ReportCommunicationSummary =
            if (
                analysisStatus == ReportCommunicationAnalysisStatus.Unknown &&
                !hasCommunicationData
            ) {
                copy(analysisStatus = ReportCommunicationAnalysisStatus.Empty)
            } else {
                this
            }

        private fun isSameActiveChild(childId: Long): Boolean {
            val activeChildState = _uiState.value.activeChildState
            return activeChildState is ActiveChildProfileState.Selected &&
                activeChildState.profile.childId == childId
        }
    }
