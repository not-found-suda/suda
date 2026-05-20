@file:Suppress("MagicNumber")

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
import java.time.LocalDate
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
    val selectedPeriod: ReportCommunicationSummaryPeriod = ReportCommunicationSummaryPeriod.Daily,
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

enum class ReportCommunicationSummaryPeriod(
    val label: String,
) {
    Daily("일간"),
    Weekly("주간"),
    Monthly("월간"),
    Total("종합 리포트"),
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
                            period = _uiState.value.selectedPeriod,
                        )
                    }
                }
        }

        fun selectPeriod(period: ReportCommunicationSummaryPeriod) {
            if (_uiState.value.selectedPeriod == period) {
                return
            }

            _uiState.value =
                _uiState.value.copy(
                    selectedPeriod = period,
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
                        period = _uiState.value.selectedPeriod,
                    )
                }
        }

        private suspend fun loadCommunicationSummary(
            childId: Long,
            period: ReportCommunicationSummaryPeriod,
        ) {
            val result =
                withContext(Dispatchers.IO) {
                    val anchorDate =
                        reportRepository
                            .getCommunicationSummary(
                                childId = childId,
                                sessionLimit = COMMUNICATION_ANCHOR_SESSION_LIMIT,
                            ).getOrNull()
                            ?.preferredAnchorDateOrNull()
                            ?: LocalDate.now()
                    reportRepository.getCommunicationSummary(
                        childId = childId,
                        filter = period.toFilterState(anchorDate = anchorDate),
                        sessionLimit = COMMUNICATION_ANCHOR_SESSION_LIMIT,
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

        private fun ReportCommunicationSummary.preferredAnchorDateOrNull(): LocalDate? =
            recentSessions
                .firstOrNull()
                ?.startedAt
                ?.take(REPORT_DATE_LENGTH)
                ?.toReportDateOrNull()
                ?: generatedAt
                    ?.take(REPORT_DATE_LENGTH)
                    ?.toReportDateOrNull()

        private fun String.toReportDateOrNull(): LocalDate? =
            runCatching {
                trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(LocalDate::parse)
            }.getOrNull()

        private fun ReportCommunicationSummaryPeriod.toFilterState(
            anchorDate: LocalDate,
        ): ReportFilterState =
            when (this) {
                ReportCommunicationSummaryPeriod.Daily ->
                    ReportFilterState(
                        from = anchorDate.toString(),
                        to = anchorDate.toString(),
                    )

                ReportCommunicationSummaryPeriod.Weekly ->
                    ReportFilterState(
                        from = anchorDate.minusDays(6).toString(),
                        to = anchorDate.toString(),
                    )

                ReportCommunicationSummaryPeriod.Monthly ->
                    ReportFilterState(
                        from = anchorDate.minusDays(29).toString(),
                        to = anchorDate.toString(),
                    )

                ReportCommunicationSummaryPeriod.Total -> ReportFilterState()
            }
    }

private const val COMMUNICATION_ANCHOR_SESSION_LIMIT = 1
private const val REPORT_DATE_LENGTH = 10
