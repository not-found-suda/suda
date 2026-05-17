package com.ssafy.mobile.feature.report.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionDetail
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

data class ReportQuizSessionDetailUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val detailState: ReportQuizSessionDetailState = ReportQuizSessionDetailState.Idle,
)

sealed interface ReportQuizSessionDetailState {
    data object Idle : ReportQuizSessionDetailState

    data object Loading : ReportQuizSessionDetailState

    data class Success(
        val detail: ReportQuizSessionDetail,
    ) : ReportQuizSessionDetailState

    data class Error(
        val message: String,
    ) : ReportQuizSessionDetailState
}

@HiltViewModel
class ReportQuizSessionDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val reportRepository: ReportRepository,
    ) : ViewModel() {
        private val sessionId: Long =
            checkNotNull(savedStateHandle["sessionId"]) {
                "ReportQuizSessionDetail route requires sessionId."
            }

        private val _uiState = MutableStateFlow(ReportQuizSessionDetailUiState())
        val uiState: StateFlow<ReportQuizSessionDetailUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null

        init {
            loadActiveChildProfile()
        }

        fun loadActiveChildProfile(showLoading: Boolean = true) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    if (showLoading) {
                        _uiState.value =
                            _uiState.value.copy(
                                activeChildState = ActiveChildProfileState.Loading,
                                detailState = ReportQuizSessionDetailState.Idle,
                            )
                    }

                    val state =
                        withContext(Dispatchers.IO) {
                            activeChildProfileManager.getActiveChildProfile()
                        }
                    val shouldShowDetailLoading =
                        showLoading ||
                            _uiState.value.detailState is ReportQuizSessionDetailState.Idle

                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = state,
                            detailState =
                                if (state is ActiveChildProfileState.Selected) {
                                    if (shouldShowDetailLoading) {
                                        ReportQuizSessionDetailState.Loading
                                    } else {
                                        _uiState.value.detailState
                                    }
                                } else {
                                    ReportQuizSessionDetailState.Idle
                                },
                        )

                    if (state is ActiveChildProfileState.Selected) {
                        loadQuizSessionDetail(childId = state.profile.childId)
                    }
                }
        }

        private suspend fun loadQuizSessionDetail(childId: Long) {
            val result =
                withContext(Dispatchers.IO) {
                    reportRepository.getQuizSessionDetail(
                        childId = childId,
                        sessionId = sessionId,
                    )
                }

            if (!isSameActiveChild(childId = childId)) {
                return
            }

            _uiState.value =
                result.fold(
                    onSuccess = { detail ->
                        _uiState.value.copy(
                            detailState = ReportQuizSessionDetailState.Success(detail),
                        )
                    },
                    onFailure = { throwable ->
                        _uiState.value.copy(
                            detailState =
                                ReportQuizSessionDetailState.Error(
                                    throwable.message ?: "퀴즈 기록 상세를 불러오지 못했습니다.",
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
