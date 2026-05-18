package com.ssafy.mobile.feature.learning.presentation.wordlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.audio.AudioPlayer
import com.ssafy.mobile.feature.learning.domain.model.DEFAULT_LEARNING_DIFFICULTY
import com.ssafy.mobile.feature.learning.domain.repository.LearningWordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LearningWordListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val wordRepository: LearningWordRepository,
        private val audioPlayer: AudioPlayer,
    ) : ViewModel() {
        val categoryId: Long = checkNotNull(savedStateHandle["categoryId"])
        val categoryName: String? = savedStateHandle["categoryName"]
        val difficulty: String = savedStateHandle["difficulty"] ?: DEFAULT_LEARNING_DIFFICULTY
        private val targetWordId: Long? =
            savedStateHandle
                .get<Long>("targetWordId")
                ?.takeIf { it > 0L }

        private val _uiState =
            MutableStateFlow<LearningWordListUiState>(LearningWordListUiState.Loading)
        val uiState: StateFlow<LearningWordListUiState> = _uiState.asStateFlow()

        private var fetchJob: Job? = null
        private var currentPlaybackRequestId: Long = 0

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
                            wordRepository.getWords(
                                categoryId = categoryId,
                                difficulty = difficulty,
                            )
                        }

                    result
                        .onSuccess { words ->
                            val focusedWords =
                                targetWordId
                                    ?.let { id -> words.firstOrNull { word -> word.id == id } }
                                    ?.let { word -> listOf(word) }
                                    ?: words
                            _uiState.value =
                                if (focusedWords.isEmpty()) {
                                    LearningWordListUiState.Empty
                                } else {
                                    LearningWordListUiState.Success(
                                        words = focusedWords,
                                        currentIndex = 0,
                                    )
                                }
                        }.onFailure { throwable ->
                            _uiState.value =
                                LearningWordListUiState.Error(
                                    throwable.message ?: "단어 목록을 불러오지 못했습니다.",
                                )
                        }
                }
        }

        fun playCurrentWordAudio() {
            val state = _uiState.value as? LearningWordListUiState.Success
            val word = state?.currentWord
            val audioUrl = word?.audioUrl

            if (audioUrl.isNullOrBlank()) {
                updateAudioState(AudioPlaybackState.Error)
                return
            }

            val requestId = ++currentPlaybackRequestId
            updateAudioState(AudioPlaybackState.Loading)

            audioPlayer.playUrl(
                url = audioUrl,
                onPrepared = {
                    if (requestId == currentPlaybackRequestId) {
                        updateAudioState(AudioPlaybackState.Playing)
                    }
                },
                onComplete = {
                    if (requestId == currentPlaybackRequestId) {
                        updateAudioState(AudioPlaybackState.Idle)
                    }
                },
                onError = { _ ->
                    if (requestId == currentPlaybackRequestId) {
                        updateAudioState(AudioPlaybackState.Error)
                    }
                },
            )
        }

        fun nextWord() {
            updateSuccessState { state ->
                if (state.hasNext) {
                    currentPlaybackRequestId++
                    audioPlayer.stop()
                    state.copy(
                        currentIndex = state.currentIndex + 1,
                        audioState = AudioPlaybackState.Idle,
                    )
                } else {
                    state
                }
            }
        }

        fun previousWord() {
            updateSuccessState { state ->
                if (state.hasPrevious) {
                    currentPlaybackRequestId++
                    audioPlayer.stop()
                    state.copy(
                        currentIndex = state.currentIndex - 1,
                        audioState = AudioPlaybackState.Idle,
                    )
                } else {
                    state
                }
            }
        }

        fun stopAudio() {
            currentPlaybackRequestId++
            audioPlayer.stop()
            updateAudioState(AudioPlaybackState.Idle)
        }

        private fun updateAudioState(audioState: AudioPlaybackState) {
            updateSuccessState { it.copy(audioState = audioState) }
        }

        private fun updateSuccessState(
            update: (LearningWordListUiState.Success) -> LearningWordListUiState.Success,
        ) {
            _uiState.update { currentState ->
                if (currentState is LearningWordListUiState.Success) {
                    update(currentState)
                } else {
                    currentState
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            audioPlayer.release()
        }
    }
