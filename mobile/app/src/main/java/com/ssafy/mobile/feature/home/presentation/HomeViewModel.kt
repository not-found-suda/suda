package com.ssafy.mobile.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val weeklyActivityState: HomeWeeklyActivityState = HomeWeeklyActivityState.Idle,
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

        init {
            loadActiveChildProfile()
        }

        fun loadActiveChildProfile(showLoading: Boolean = true) {
            viewModelScope.launch {
                if (showLoading) {
                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = ActiveChildProfileState.Loading,
                            weeklyActivityState = HomeWeeklyActivityState.Idle,
                            resumeQuizState = HomeResumeQuizState.Idle,
                        )
                }
                val state =
                    withContext(Dispatchers.IO) {
                        activeChildProfileManager.getActiveChildProfile()
                    }
                val shouldShowLearningLoading =
                    showLoading ||
                        _uiState.value.weeklyActivityState is HomeWeeklyActivityState.Idle ||
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
                    val weeklyActivityResult =
                        reportRepository.getQuizSessions(
                            childId = childId,
                            page = FIRST_PAGE,
                            size = WEEKLY_ACTIVITY_PAGE_SIZE,
                            filter = currentWeekReportFilter(),
                        )
                    val resumeQuizResult =
                        reportRepository.getQuizSessions(
                            childId = childId,
                            page = FIRST_PAGE,
                            size = RESUME_QUIZ_PAGE_SIZE,
                            filter = ReportFilterState(status = QUIZ_STATUS_IN_PROGRESS),
                        )
                    weeklyActivityResult to resumeQuizResult
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
                    weeklyActivityState = result.first.toWeeklyActivityState(),
                    resumeQuizState = result.second.toResumeQuizState(),
                )
        }
    }

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

private fun currentWeekReportFilter(): ReportFilterState {
    val today = LocalDate.now()
    return ReportFilterState(
        from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
        to = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString(),
    )
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
private const val REPORT_DATE_LENGTH = 10
private const val QUIZ_STATUS_IN_PROGRESS = "IN_PROGRESS"
