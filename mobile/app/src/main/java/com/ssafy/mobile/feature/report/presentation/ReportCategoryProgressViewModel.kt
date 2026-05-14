package com.ssafy.mobile.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportCategoryProgressPage
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

data class ReportCategoryProgressUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val categoryProgressState: ReportCategoryProgressState = ReportCategoryProgressState.Idle,
    val filterUiState: ReportFilterUiState = ReportFilterUiState(),
)

sealed interface ReportCategoryProgressState {
    data object Idle : ReportCategoryProgressState

    data object Loading : ReportCategoryProgressState

    data object Empty : ReportCategoryProgressState

    data class Success(
        val page: ReportCategoryProgressPage,
    ) : ReportCategoryProgressState

    data class Error(
        val message: String,
    ) : ReportCategoryProgressState
}

@HiltViewModel
class ReportCategoryProgressViewModel
    @Inject
    constructor(
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val reportRepository: ReportRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReportCategoryProgressUiState())
        val uiState: StateFlow<ReportCategoryProgressUiState> = _uiState.asStateFlow()

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
                            categoryProgressState = ReportCategoryProgressState.Idle,
                        )

                    val state =
                        withContext(Dispatchers.IO) {
                            activeChildProfileManager.getActiveChildProfile()
                        }

                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = state,
                            categoryProgressState =
                                if (state is ActiveChildProfileState.Selected) {
                                    ReportCategoryProgressState.Loading
                                } else {
                                    ReportCategoryProgressState.Idle
                                },
                        )

                    if (state is ActiveChildProfileState.Selected) {
                        loadCategoryProgress(
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
            reloadCategoryProgressWithCurrentChild()
        }

        fun resetFilter() {
            appliedFilter = defaultReportFilterState()
            _uiState.value =
                _uiState.value.copy(
                    filterUiState = ReportFilterUiState(),
                )
            reloadCategoryProgressWithCurrentChild()
        }

        private fun reloadCategoryProgressWithCurrentChild() {
            val childState =
                _uiState.value.activeChildState as? ActiveChildProfileState.Selected
                    ?: return
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(
                            categoryProgressState = ReportCategoryProgressState.Loading,
                        )
                    loadCategoryProgress(
                        childId = childState.profile.childId,
                        filter = appliedFilter,
                    )
                }
        }

        private suspend fun loadCategoryProgress(
            childId: Long,
            filter: ReportFilterState,
        ) {
            val result =
                withContext(Dispatchers.IO) {
                    reportRepository.getCategoryProgress(
                        childId = childId,
                        filter = filter,
                    )
                }

            if (!isSameActiveChild(childId = childId)) {
                return
            }

            _uiState.value =
                result.fold(
                    onSuccess = { page ->
                        _uiState.value.copy(
                            categoryProgressState =
                                if (page.hasCategories) {
                                    ReportCategoryProgressState.Success(page)
                                } else {
                                    ReportCategoryProgressState.Empty
                                },
                        )
                    },
                    onFailure = { throwable ->
                        _uiState.value.copy(
                            categoryProgressState =
                                ReportCategoryProgressState.Error(
                                    throwable.message ?: "카테고리별 리포트를 불러오지 못했습니다.",
                                ),
                        )
                    },
                )
        }

        private fun isSameActiveChild(childId: Long): Boolean {
            val activeChildState = _uiState.value.activeChildState
            return activeChildState is ActiveChildProfileState.Selected &&
                activeChildState.profile.childId == childId
        }
    }
