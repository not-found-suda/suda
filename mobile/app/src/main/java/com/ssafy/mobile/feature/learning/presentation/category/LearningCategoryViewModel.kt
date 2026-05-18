package com.ssafy.mobile.feature.learning.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.learning.domain.model.LearningCategory
import com.ssafy.mobile.feature.learning.domain.repository.LearningCategoryRepository
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

sealed interface LearningCategoryUiState {
    data object Loading : LearningCategoryUiState

    data class Success(
        val categories: List<LearningCategory>,
    ) : LearningCategoryUiState

    data object Empty : LearningCategoryUiState

    data class Error(
        val message: String,
    ) : LearningCategoryUiState
}

@HiltViewModel
class LearningCategoryViewModel
    @Inject
    constructor(
        private val repository: LearningCategoryRepository,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<LearningCategoryUiState>(
                LearningCategoryUiState.Loading,
            )
        val uiState: StateFlow<LearningCategoryUiState> = _uiState.asStateFlow()

        private var loadCategoriesJob: Job? = null

        init {
            loadCategories()
        }

        fun loadCategories() {
            if (loadCategoriesJob?.isActive == true) return

            _uiState.value = LearningCategoryUiState.Loading

            loadCategoriesJob =
                viewModelScope.launch {
                    try {
                        val categories =
                            withContext(Dispatchers.IO) {
                                repository.getCategories()
                            }
                        _uiState.value =
                            if (categories.isEmpty()) {
                                LearningCategoryUiState.Empty
                            } else {
                                LearningCategoryUiState.Success(categories)
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        e: Exception,
                    ) {
                        _uiState.value =
                            LearningCategoryUiState.Error(
                                e.message ?: "알 수 없는 오류가 발생했습니다.",
                            )
                    }
                }
        }
    }
