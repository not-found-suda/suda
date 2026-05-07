package com.ssafy.mobile.feature.learning.presentation.wordlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.feature.learning.domain.repository.LearningWordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LearningWordListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val wordRepository: LearningWordRepository,
    ) : ViewModel() {
        private val categoryId: Long = checkNotNull(savedStateHandle["categoryId"])
        val categoryName: String? = savedStateHandle["categoryName"]

        private val _uiState =
            MutableStateFlow<LearningWordListUiState>(LearningWordListUiState.Loading)
        val uiState: StateFlow<LearningWordListUiState> = _uiState.asStateFlow()

        private var fetchJob: Job? = null

        init {
            loadWords()
        }

        fun loadWords() {
            if (fetchJob?.isActive == true) return

            fetchJob =
                viewModelScope.launch {
                    _uiState.value = LearningWordListUiState.Loading
                    val result =
                        withContext(Dispatchers.IO) {
                            wordRepository.getWords(categoryId)
                        }

                    result
                        .onSuccess { words ->
                            _uiState.value =
                                if (words.isEmpty()) {
                                    LearningWordListUiState.Empty
                                } else {
                                    LearningWordListUiState.Success(words)
                                }
                        }.onFailure { throwable ->
                            _uiState.value =
                                LearningWordListUiState.Error(
                                    throwable.message ?: "단어 목록을 불러오지 못했습니다.",
                                )
                        }
                }
        }
    }
