package com.ssafy.mobile.feature.report.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.learning.domain.repository.LearningCategoryRepository
import com.ssafy.mobile.feature.report.domain.model.ReportFilterState
import com.ssafy.mobile.feature.report.domain.model.ReportWeakWordPage
import com.ssafy.mobile.feature.report.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportWeakWordsUiState(
    val activeChildState: ActiveChildProfileState = ActiveChildProfileState.Loading,
    val weakWordsState: ReportWeakWordsState = ReportWeakWordsState.Idle,
    val filterUiState: ReportFilterUiState = ReportFilterUiState(),
)

private data class WeakWordsLoadMoreRequest(
    val childId: Long,
    val weakWordsState: ReportWeakWordsState.Success,
)

sealed interface ReportWeakWordsState {
    data object Idle : ReportWeakWordsState

    data object Loading : ReportWeakWordsState

    data object Empty : ReportWeakWordsState

    data class Success(
        val page: ReportWeakWordPage,
        val isLoadingMore: Boolean = false,
        val loadMoreErrorMessage: String? = null,
    ) : ReportWeakWordsState

    data class Error(
        val message: String,
    ) : ReportWeakWordsState
}

@HiltViewModel
class ReportWeakWordsViewModel
    @Inject
    constructor(
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val reportRepository: ReportRepository,
        private val learningCategoryRepository: LearningCategoryRepository,
        private val filterSelectionStore: ReportFilterSelectionStore,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                ReportWeakWordsUiState(
                    filterUiState =
                        ReportFilterUiState(
                            input = filterSelectionStore.currentInput(),
                            anchorDate = filterSelectionStore.currentAnchorDate(),
                            hasAppliedFilter = true,
                        ),
                ),
            )
        val uiState: StateFlow<ReportWeakWordsUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null
        private var loadMoreJob: Job? = null
        private var loadCategoriesJob: Job? = null
        private var appliedFilter =
            filterSelectionStore
                .currentInput()
                .toWeakWordsFilter()
                .getOrDefault(
                    defaultReportFilterState(filterSelectionStore.currentAnchorDate()),
                )

        init {
            loadFilterCategories()
            loadActiveChildProfile()
        }

        fun loadActiveChildProfile(showLoading: Boolean = true) {
            loadJob?.cancel()
            loadMoreJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    if (showLoading) {
                        _uiState.value =
                            _uiState.value.copy(
                                activeChildState = ActiveChildProfileState.Loading,
                                weakWordsState = ReportWeakWordsState.Idle,
                            )
                    }

                    val state =
                        withContext(Dispatchers.IO) {
                            activeChildProfileManager.getActiveChildProfile()
                        }
                    val shouldShowWeakWordsLoading =
                        showLoading || _uiState.value.weakWordsState is ReportWeakWordsState.Idle

                    _uiState.value =
                        _uiState.value.copy(
                            activeChildState = state,
                            weakWordsState =
                                if (state is ActiveChildProfileState.Selected) {
                                    if (shouldShowWeakWordsLoading) {
                                        ReportWeakWordsState.Loading
                                    } else {
                                        _uiState.value.weakWordsState
                                    }
                                } else {
                                    ReportWeakWordsState.Idle
                                },
                        )

                    if (state is ActiveChildProfileState.Selected) {
                        loadWeakWords(
                            childId = state.profile.childId,
                            filter = appliedFilter,
                        )
                    }
                }
        }

        fun loadMoreWeakWords() {
            val request = createLoadMoreRequest(state = _uiState.value) ?: return
            val childId = request.childId
            val weakWordsState = request.weakWordsState

            loadMoreJob?.cancel()
            loadMoreJob =
                viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(
                            weakWordsState =
                                weakWordsState.copy(
                                    isLoadingMore = true,
                                    loadMoreErrorMessage = null,
                                ),
                        )

                    val nextPage = weakWordsState.page.page + 1
                    val result =
                        withContext(Dispatchers.IO) {
                            reportRepository.getWeakWords(
                                childId = childId,
                                page = nextPage,
                                size = DEFAULT_WEAK_WORDS_PAGE_SIZE,
                                filter = appliedFilter,
                            )
                        }

                    if (!isSameActiveChild(childId = childId)) {
                        return@launch
                    }

                    _uiState.value =
                        result.fold(
                            onSuccess = { page ->
                                _uiState.value.copy(
                                    weakWordsState =
                                        weakWordsState.copy(
                                            page =
                                                page.copy(
                                                    words = weakWordsState.page.words + page.words,
                                                ),
                                            isLoadingMore = false,
                                        ),
                                )
                            },
                            onFailure = { throwable ->
                                _uiState.value.copy(
                                    weakWordsState =
                                        weakWordsState.copy(
                                            isLoadingMore = false,
                                            loadMoreErrorMessage =
                                                throwable.message ?: "취약 단어를 더 불러오지 못했습니다.",
                                        ),
                                )
                            },
                        )
                }
        }

        fun loadFilterCategories() {
            if (loadCategoriesJob?.isActive == true) return

            _uiState.value =
                _uiState.value.copy(
                    filterUiState =
                        _uiState.value.filterUiState.copy(
                            isCategoryLoading = true,
                            categoryErrorMessage = null,
                        ),
                )

            loadCategoriesJob =
                viewModelScope.launch {
                    try {
                        val categories =
                            withContext(Dispatchers.IO) {
                                learningCategoryRepository.getCategories()
                            }
                        _uiState.value =
                            _uiState.value.copy(
                                filterUiState =
                                    _uiState.value.filterUiState.copy(
                                        categoryOptions =
                                            categories.map { category ->
                                                ReportFilterCategoryOption(
                                                    categoryId = category.categoryId,
                                                    name = category.name,
                                                )
                                            },
                                        isCategoryLoading = false,
                                        categoryErrorMessage = null,
                                    ),
                            )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        e: Exception,
                    ) {
                        _uiState.value =
                            _uiState.value.copy(
                                filterUiState =
                                    _uiState.value.filterUiState.copy(
                                        isCategoryLoading = false,
                                        categoryErrorMessage = e.message ?: "카테고리를 불러오지 못했습니다.",
                                    ),
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
                    .toWeakWordsFilter()
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
            reloadWeakWordsWithCurrentChild()
        }

        fun resetFilter() {
            appliedFilter = defaultReportFilterState(filterSelectionStore.currentAnchorDate())
            _uiState.value =
                _uiState.value.copy(
                    filterUiState =
                        _uiState.value.filterUiState.copy(
                            input = ReportFilterInputState(),
                            hasAppliedFilter = false,
                            errorMessage = null,
                        ),
                )
            reloadWeakWordsWithCurrentChild()
        }

        private fun reloadWeakWordsWithCurrentChild() {
            val childState =
                _uiState.value.activeChildState as? ActiveChildProfileState.Selected
                    ?: return
            loadJob?.cancel()
            loadMoreJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.value =
                        _uiState.value.copy(
                            weakWordsState = ReportWeakWordsState.Loading,
                        )
                    loadWeakWords(
                        childId = childState.profile.childId,
                        filter = appliedFilter,
                    )
                }
        }

        private suspend fun loadWeakWords(
            childId: Long,
            filter: ReportFilterState,
        ) {
            val result =
                withContext(Dispatchers.IO) {
                    reportRepository.getWeakWords(
                        childId = childId,
                        page = FIRST_PAGE,
                        size = DEFAULT_WEAK_WORDS_PAGE_SIZE,
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
                            weakWordsState =
                                if (page.hasWords) {
                                    ReportWeakWordsState.Success(page)
                                } else {
                                    ReportWeakWordsState.Empty
                                },
                        )
                    },
                    onFailure = { throwable ->
                        _uiState.value.copy(
                            weakWordsState =
                                ReportWeakWordsState.Error(
                                    throwable.message ?: "취약 단어를 불러오지 못했습니다.",
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
            state: ReportWeakWordsUiState,
        ): WeakWordsLoadMoreRequest? {
            val childState = state.activeChildState as? ActiveChildProfileState.Selected
            val weakWordsState = state.weakWordsState as? ReportWeakWordsState.Success
            if (childState == null || weakWordsState == null) {
                return null
            }

            val hasMore = weakWordsState.page.page < weakWordsState.page.totalPages
            return if (!weakWordsState.isLoadingMore && hasMore) {
                WeakWordsLoadMoreRequest(
                    childId = childState.profile.childId,
                    weakWordsState = weakWordsState,
                )
            } else {
                null
            }
        }
    }

private const val FIRST_PAGE = 1
private const val DEFAULT_WEAK_WORDS_PAGE_SIZE = 20
