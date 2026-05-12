package com.ssafy.mobile.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.report.domain.model.ReportQuizSessionPage
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

data class ReportQuizSessionsUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val quizSessionsState: ReportQuizSessionsState = ReportQuizSessionsState.Idle,
)

private data class QuizSessionsLoadMoreRequest(
    val childId: Long,
    val quizSessionsState: ReportQuizSessionsState.Success,
)

sealed interface ReportQuizSessionsState {
    data object Idle : ReportQuizSessionsState

    data object Loading : ReportQuizSessionsState

    data object Empty : ReportQuizSessionsState

    data class Success(
        val page: ReportQuizSessionPage,
        val isLoadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
    ) : ReportQuizSessionsState

    data class Error(
        val message: String,
    ) : ReportQuizSessionsState
}

@HiltViewModel
class ReportQuizSessionsViewModel
    @Inject
    constructor(
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val reportRepository: ReportRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ReportQuizSessionsUiState())
        val uiState: StateFlow<ReportQuizSessionsUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null
        private var loadMoreJob: Job? = null

        init {
            loadActiveChildProfile()
        }

        fun loadActiveChildProfile() {
            loadJob?.cancel()
            loadMoreJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = ActiveChildProfileState.Loading,
                            quizSessionsState = ReportQuizSessionsState.Idle,
                        )

                    val state =
                        withContext(Dispatchers.IO) {
                            activeChildProfileManager.getActiveChildProfile()
                        }

                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = state,
                            quizSessionsState =
                                if (state is ActiveChildProfileState.Selected) {
                                    ReportQuizSessionsState.Loading
                                } else {
                                    ReportQuizSessionsState.Idle
                                },
                        )

                    if (state is ActiveChildProfileState.Selected) {
                        loadQuizSessions(childId = state.profile.childId)
                    }
                }
        }

        fun loadMoreQuizSessions() {
            val request = createLoadMoreRequest(state = _uiState.value) ?: return
            val childId = request.childId
            val quizSessionsState = request.quizSessionsState

            loadMoreJob?.cancel()
            loadMoreJob =
                viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(
                            quizSessionsState =
                                quizSessionsState.copy(
                                    isLoadingMore = true,
                                    loadMoreErrorMessage = null,
                                ),
                        )

                    val nextPage = quizSessionsState.page.page + 1
                    val result =
                        withContext(Dispatchers.IO) {
                            reportRepository.getQuizSessions(
                                childId = childId,
                                page = nextPage,
                                size = DEFAULT_QUIZ_SESSIONS_PAGE_SIZE,
                            )
                        }

                    if (!isSameActiveChild(childId = childId)) {
                        return@launch
                    }

                    _uiState.value =
                        result.fold(
                            onSuccess = { page ->
                                _uiState.value.copy(
                                    quizSessionsState =
                                        quizSessionsState.copy(
                                            page =
                                                page.copy(
                                                    sessions =
                                                        quizSessionsState.page.sessions +
                                                            page.sessions,
                                                ),
                                            isLoadingMore = false,
                                        ),
                                )
                            },
                            onFailure = { throwable ->
                                _uiState.value.copy(
                                    quizSessionsState =
                                        quizSessionsState.copy(
                                            isLoadingMore = false,
                                            loadMoreErrorMessage =
                                                throwable.message ?: "퀴즈 기록을 더 불러오지 못했습니다.",
                                        ),
                                )
                            },
                        )
                }
        }

        private suspend fun loadQuizSessions(childId: Long) {
            val result =
                withContext(Dispatchers.IO) {
                    reportRepository.getQuizSessions(
                        childId = childId,
                        page = FIRST_PAGE,
                        size = DEFAULT_QUIZ_SESSIONS_PAGE_SIZE,
                    )
                }

            if (!isSameActiveChild(childId = childId)) {
                return
            }

            _uiState.value =
                result.fold(
                    onSuccess = { page ->
                        _uiState.value.copy(
                            quizSessionsState =
                                if (page.hasSessions) {
                                    ReportQuizSessionsState.Success(page)
                                } else {
                                    ReportQuizSessionsState.Empty
                                },
                        )
                    },
                    onFailure = { throwable ->
                        _uiState.value.copy(
                            quizSessionsState =
                                ReportQuizSessionsState.Error(
                                    throwable.message ?: "퀴즈 기록을 불러오지 못했습니다.",
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

        private fun createLoadMoreRequest(
            state: ReportQuizSessionsUiState,
        ): QuizSessionsLoadMoreRequest? {
            val childState = state.activeChildState as? ActiveChildProfileState.Selected
            val quizSessionsState = state.quizSessionsState as? ReportQuizSessionsState.Success
            if (childState == null || quizSessionsState == null) {
                return null
            }

            val hasMore = quizSessionsState.page.page < quizSessionsState.page.totalPages
            return if (!quizSessionsState.isLoadingMore && hasMore) {
                QuizSessionsLoadMoreRequest(
                    childId = childState.profile.childId,
                    quizSessionsState = quizSessionsState,
                )
            } else {
                null
            }
        }
    }

private const val FIRST_PAGE = 1
private const val DEFAULT_QUIZ_SESSIONS_PAGE_SIZE = 20
