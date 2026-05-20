@file:Suppress("ComplexCondition", "MagicNumber")

package com.ssafy.mobile.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationAnalysisStatus
import com.ssafy.mobile.feature.report.domain.model.ReportCommunicationSummary
import com.ssafy.mobile.feature.report.domain.model.ReportFilterState
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSession
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionPage
import com.ssafy.mobile.feature.report.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val weeklyActivityState: HomeWeeklyActivityState = HomeWeeklyActivityState.Idle,
    val selectedCommunicationDate: LocalDate = LocalDate.now(),
    val selectedCommunicationPeriod: HomeCommunicationPeriod = HomeCommunicationPeriod.Daily,
    val communicationInsightToggleResetKey: Int = 0,
    val communicationInsightState: HomeCommunicationInsightState =
        HomeCommunicationInsightState.Idle,
    val resumeQuizState: HomeResumeQuizState = HomeResumeQuizState.Idle,
)

sealed interface HomeWeeklyActivityState {
    data object Idle : HomeWeeklyActivityState

    data object Loading : HomeWeeklyActivityState

    data class Success(
        val activityDates: Set<LocalDate>,
    ) : HomeWeeklyActivityState

    data object Error : HomeWeeklyActivityState
}

sealed interface HomeCommunicationInsightState {
    data object Idle : HomeCommunicationInsightState

    data object Loading : HomeCommunicationInsightState

    data object Empty : HomeCommunicationInsightState

    data object Processing : HomeCommunicationInsightState

    data class Success(
        val summary: ReportCommunicationSummary,
    ) : HomeCommunicationInsightState

    data class Error(
        val message: String,
    ) : HomeCommunicationInsightState
}

enum class HomeCommunicationPeriod(
    val label: String,
) {
    Daily("일간"),
    Weekly("주간"),
    Monthly("월간"),
    Total("종합 리포트"),
}

sealed interface HomeResumeQuizState {
    data object Idle : HomeResumeQuizState

    data object Loading : HomeResumeQuizState

    data object Empty : HomeResumeQuizState

    data class Success(
        val sessions: List<ReportQuizSession>,
        val totalElements: Long,
    ) : HomeResumeQuizState

    data class Error(
        val message: String,
    ) : HomeResumeQuizState
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val reportRepository: ReportRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
        private var communicationInsightPollingJob: Job? = null

        init {
            loadActiveChildProfile()
        }

        fun selectCommunicationDate(date: LocalDate) {
            if (_uiState.value.selectedCommunicationDate == date) {
                return
            }

            _uiState.value =
                _uiState.value.copy(
                    selectedCommunicationDate = date,
                    communicationInsightToggleResetKey =
                        _uiState.value.communicationInsightToggleResetKey + 1,
                    communicationInsightState = HomeCommunicationInsightState.Loading,
                )
            communicationInsightPollingJob?.cancel()

            val childState =
                _uiState.value.activeChildState as? ActiveChildProfileState.Selected
                    ?: return
            loadCommunicationInsight(
                childId = childState.profile.childId,
                anchorDate = date,
                period = _uiState.value.selectedCommunicationPeriod,
            )
        }

        fun selectCommunicationPeriod(period: HomeCommunicationPeriod) {
            if (_uiState.value.selectedCommunicationPeriod == period) {
                return
            }

            _uiState.value =
                _uiState.value.copy(
                    selectedCommunicationPeriod = period,
                    communicationInsightToggleResetKey =
                        _uiState.value.communicationInsightToggleResetKey + 1,
                    communicationInsightState = HomeCommunicationInsightState.Loading,
                )
            communicationInsightPollingJob?.cancel()

            val childState =
                _uiState.value.activeChildState as? ActiveChildProfileState.Selected
                    ?: return
            loadCommunicationInsight(
                childId = childState.profile.childId,
                anchorDate = _uiState.value.selectedCommunicationDate,
                period = period,
            )
        }

        fun loadActiveChildProfile(showLoading: Boolean = true) {
            viewModelScope.launch {
                if (showLoading) {
                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = ActiveChildProfileState.Loading,
                            selectedCommunicationPeriod = HomeCommunicationPeriod.Daily,
                            weeklyActivityState = HomeWeeklyActivityState.Idle,
                            communicationInsightToggleResetKey =
                                _uiState.value.communicationInsightToggleResetKey + 1,
                            communicationInsightState = HomeCommunicationInsightState.Idle,
                            resumeQuizState = HomeResumeQuizState.Idle,
                        )
                }
                val state =
                    withContext(Dispatchers.IO) {
                        activeChildProfileManager.getActiveChildProfile()
                    }
                val shouldLoadCommunication =
                    _uiState.value.communicationInsightState is HomeCommunicationInsightState.Idle
                val shouldShowLearningLoading =
                    showLoading ||
                        _uiState.value.weeklyActivityState is HomeWeeklyActivityState.Idle ||
                        shouldLoadCommunication ||
                        _uiState.value.resumeQuizState is HomeResumeQuizState.Idle
                _uiState.value =
                    _uiState.value.copy(
                        activeChildState = state,
                        weeklyActivityState =
                            if (state is ActiveChildProfileState.Selected) {
                                if (shouldShowLearningLoading) {
                                    HomeWeeklyActivityState.Loading
                                } else {
                                    _uiState.value.weeklyActivityState
                                }
                            } else {
                                HomeWeeklyActivityState.Idle
                            },
                        communicationInsightState =
                            if (state is ActiveChildProfileState.Selected) {
                                if (shouldShowLearningLoading) {
                                    HomeCommunicationInsightState.Loading
                                } else {
                                    _uiState.value.communicationInsightState
                                }
                            } else {
                                HomeCommunicationInsightState.Idle
                            },
                        resumeQuizState =
                            if (state is ActiveChildProfileState.Selected) {
                                if (shouldShowLearningLoading) {
                                    HomeResumeQuizState.Loading
                                } else {
                                    _uiState.value.resumeQuizState
                                }
                            } else {
                                HomeResumeQuizState.Idle
                            },
                    )
                if (state is ActiveChildProfileState.Selected) {
                    loadHomeLearningState(childId = state.profile.childId)
                }
            }
        }

        private suspend fun loadHomeLearningState(childId: Long) {
            val result =
                withContext(Dispatchers.IO) {
                    val communicationAnchorResult =
                        reportRepository.getCommunicationSummary(
                            childId = childId,
                            sessionLimit = LATEST_COMMUNICATION_SESSION_LIMIT,
                        )
                    val selectedDate =
                        communicationAnchorResult
                            .getOrNull()
                            ?.preferredAnchorDateOrNull()
                            ?: _uiState.value.selectedCommunicationDate
                    val weeklyActivityResult =
                        reportRepository.getQuizSessions(
                            childId = childId,
                            page = FIRST_PAGE,
                            size = WEEKLY_ACTIVITY_PAGE_SIZE,
                            filter = selectedDate.toCurrentWeekReportFilter(),
                        )
                    val selectedPeriod = _uiState.value.selectedCommunicationPeriod
                    val communicationInsightResult =
                        reportRepository.getCommunicationSummary(
                            childId = childId,
                            filter = selectedPeriod.toReportFilter(anchorDate = selectedDate),
                            sessionLimit = HOME_COMMUNICATION_SESSION_LIMIT,
                        )
                    val resumeQuizResult =
                        reportRepository.getQuizSessions(
                            childId = childId,
                            page = FIRST_PAGE,
                            size = RESUME_QUIZ_PAGE_SIZE,
                            filter = ReportFilterState(status = QUIZ_STATUS_IN_PROGRESS),
                        )
                    HomeLearningResult(
                        weeklyActivityResult = weeklyActivityResult,
                        selectedCommunicationDate = selectedDate,
                        selectedCommunicationPeriod = selectedPeriod,
                        communicationInsightResult = communicationInsightResult,
                        resumeQuizResult = resumeQuizResult,
                    )
                }

            val currentActiveChildState = _uiState.value.activeChildState
            if (
                currentActiveChildState !is ActiveChildProfileState.Selected ||
                currentActiveChildState.profile.childId != childId
            ) {
                return
            }

            _uiState.value =
                _uiState.value.copy(
                    weeklyActivityState = result.weeklyActivityResult.toWeeklyActivityState(),
                    selectedCommunicationDate = result.selectedCommunicationDate,
                    communicationInsightToggleResetKey =
                        _uiState.value.communicationInsightToggleResetKey + 1,
                    communicationInsightState =
                        result.communicationInsightResult.toCommunicationInsightState(),
                    resumeQuizState = result.resumeQuizResult.toResumeQuizState(),
                )
            startCommunicationInsightPollingIfNeeded(
                childId = childId,
                anchorDate = result.selectedCommunicationDate,
                period = result.selectedCommunicationPeriod,
                result = result.communicationInsightResult,
            )
        }

        private fun loadCommunicationInsight(
            childId: Long,
            anchorDate: LocalDate,
            period: HomeCommunicationPeriod,
        ) {
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        reportRepository.getCommunicationSummary(
                            childId = childId,
                            filter = period.toReportFilter(anchorDate = anchorDate),
                            sessionLimit = HOME_COMMUNICATION_SESSION_LIMIT,
                        )
                    }

                val currentState = _uiState.value.activeChildState
                if (
                    currentState !is ActiveChildProfileState.Selected ||
                    currentState.profile.childId != childId ||
                    _uiState.value.selectedCommunicationDate != anchorDate ||
                    _uiState.value.selectedCommunicationPeriod != period
                ) {
                    return@launch
                }

                _uiState.value =
                    _uiState.value.copy(
                        communicationInsightState = result.toCommunicationInsightState(),
                    )
                startCommunicationInsightPollingIfNeeded(
                    childId = childId,
                    anchorDate = anchorDate,
                    period = period,
                    result = result,
                )
            }
        }

        private fun startCommunicationInsightPollingIfNeeded(
            childId: Long,
            anchorDate: LocalDate,
            period: HomeCommunicationPeriod,
            result: Result<ReportCommunicationSummary>,
        ) {
            val status = result.getOrNull()?.analysisStatus
            if (
                status != ReportCommunicationAnalysisStatus.Pending &&
                status != ReportCommunicationAnalysisStatus.Processing
            ) {
                communicationInsightPollingJob?.cancel()
                communicationInsightPollingJob = null
                return
            }

            communicationInsightPollingJob?.cancel()
            communicationInsightPollingJob =
                viewModelScope.launch {
                    repeat(COMMUNICATION_ANALYSIS_POLLING_ATTEMPTS) {
                        delay(COMMUNICATION_ANALYSIS_POLLING_INTERVAL_MS)

                        val currentState = _uiState.value.activeChildState
                        if (
                            currentState !is ActiveChildProfileState.Selected ||
                            currentState.profile.childId != childId ||
                            _uiState.value.selectedCommunicationDate != anchorDate ||
                            _uiState.value.selectedCommunicationPeriod != period
                        ) {
                            return@launch
                        }

                        val refreshedResult =
                            withContext(Dispatchers.IO) {
                                reportRepository.getCommunicationSummary(
                                    childId = childId,
                                    filter = period.toReportFilter(anchorDate = anchorDate),
                                    sessionLimit = HOME_COMMUNICATION_SESSION_LIMIT,
                                )
                            }

                        _uiState.value =
                            _uiState.value.copy(
                                communicationInsightState =
                                    refreshedResult.toCommunicationInsightState(),
                            )

                        val refreshedStatus = refreshedResult.getOrNull()?.analysisStatus
                        if (
                            refreshedStatus != ReportCommunicationAnalysisStatus.Pending &&
                            refreshedStatus != ReportCommunicationAnalysisStatus.Processing
                        ) {
                            return@launch
                        }
                    }
                }
        }
    }

private data class HomeLearningResult(
    val weeklyActivityResult: Result<ReportQuizSessionPage>,
    val selectedCommunicationDate: LocalDate,
    val selectedCommunicationPeriod: HomeCommunicationPeriod,
    val communicationInsightResult: Result<ReportCommunicationSummary>,
    val resumeQuizResult: Result<ReportQuizSessionPage>,
)

private fun Result<ReportQuizSessionPage>.toWeeklyActivityState(): HomeWeeklyActivityState =
    fold(
        onSuccess = { page ->
            HomeWeeklyActivityState.Success(
                activityDates =
                    page.sessions
                        .mapNotNull { session ->
                            session.startedAt
                                ?.take(REPORT_DATE_LENGTH)
                                ?.toReportDateOrNull()
                        }.toSet(),
            )
        },
        onFailure = {
            HomeWeeklyActivityState.Error
        },
    )

private fun Result<ReportCommunicationSummary>.toCommunicationInsightState():
    HomeCommunicationInsightState =
    fold(
        onSuccess = { summary ->
            when (summary.analysisStatus) {
                ReportCommunicationAnalysisStatus.Pending,
                ReportCommunicationAnalysisStatus.Processing,
                ->
                    if (summary.hasCompletedCommunicationInsight()) {
                        HomeCommunicationInsightState.Success(summary)
                    } else {
                        HomeCommunicationInsightState.Processing
                    }

                ReportCommunicationAnalysisStatus.Failed ->
                    HomeCommunicationInsightState.Error("소통 분석을 완료하지 못했어요.")

                ReportCommunicationAnalysisStatus.Empty ->
                    HomeCommunicationInsightState.Empty

                ReportCommunicationAnalysisStatus.Completed,
                ReportCommunicationAnalysisStatus.Unknown,
                ->
                    if (summary.hasCommunicationData) {
                        HomeCommunicationInsightState.Success(summary)
                    } else {
                        HomeCommunicationInsightState.Empty
                    }
            }
        },
        onFailure = { throwable ->
            HomeCommunicationInsightState.Error(
                throwable.message ?: "소통 분석을 불러오지 못했습니다.",
            )
        },
    )

private fun ReportCommunicationSummary.hasCompletedCommunicationInsight(): Boolean =
    totalUtteranceCount > 0 ||
        frequentWords.isNotEmpty() ||
        strengths.isNotEmpty() ||
        developmentReference.isNotBlank() ||
        recentSessions.any { session -> session.summary.isNotBlank() }

private fun ReportCommunicationSummary.preferredAnchorDateOrNull(): LocalDate? =
    recentSessions
        .firstOrNull()
        ?.startedAt
        ?.take(REPORT_DATE_LENGTH)
        ?.toReportDateOrNull()
        ?: generatedAt
            ?.take(REPORT_DATE_LENGTH)
            ?.toReportDateOrNull()

private fun Result<ReportQuizSessionPage>.toResumeQuizState(): HomeResumeQuizState =
    fold(
        onSuccess = { page ->
            if (page.sessions.isEmpty()) {
                HomeResumeQuizState.Empty
            } else {
                HomeResumeQuizState.Success(
                    sessions = page.sessions,
                    totalElements = page.totalElements,
                )
            }
        },
        onFailure = { throwable ->
            HomeResumeQuizState.Error(
                throwable.message ?: "이어할 퀴즈를 불러오지 못했습니다.",
            )
        },
    )

private fun LocalDate.toCurrentWeekReportFilter(): ReportFilterState =
    ReportFilterState(
        from = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
        to = with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString(),
    )

private fun HomeCommunicationPeriod.toReportFilter(anchorDate: LocalDate): ReportFilterState =
    when (this) {
        HomeCommunicationPeriod.Daily ->
            ReportFilterState(
                from = anchorDate.toString(),
                to = anchorDate.toString(),
            )

        HomeCommunicationPeriod.Weekly ->
            ReportFilterState(
                from = anchorDate.minusDays(6).toString(),
                to = anchorDate.toString(),
            )

        HomeCommunicationPeriod.Monthly ->
            ReportFilterState(
                from = anchorDate.minusDays(29).toString(),
                to = anchorDate.toString(),
            )

        HomeCommunicationPeriod.Total -> ReportFilterState()
    }

private fun String.toReportDateOrNull(): LocalDate? =
    runCatching {
        trim()
            .takeIf { it.isNotBlank() }
            ?.let(LocalDate::parse)
    }.getOrNull()

private const val FIRST_PAGE = 1
private const val WEEKLY_ACTIVITY_PAGE_SIZE = 100
private const val RESUME_QUIZ_PAGE_SIZE = 3
private const val LATEST_COMMUNICATION_SESSION_LIMIT = 1
private const val HOME_COMMUNICATION_SESSION_LIMIT = 20
private const val REPORT_DATE_LENGTH = 10
private const val QUIZ_STATUS_IN_PROGRESS = "IN_PROGRESS"
private const val COMMUNICATION_ANALYSIS_POLLING_INTERVAL_MS = 3000L
private const val COMMUNICATION_ANALYSIS_POLLING_ATTEMPTS = 5
