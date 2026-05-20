package com.ssafy.mobile.feature.mypage.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.audio.AudioPlayer
import com.ssafy.mobile.core.ui.theme.AppThemeMode
import com.ssafy.mobile.core.ui.theme.AppThemeModeRepository
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import com.ssafy.mobile.feature.mypage.data.repository.AccountRepository
import com.ssafy.mobile.feature.mypage.domain.model.TtsSpeakerOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppSettingsUiState(
    val isTtsLoading: Boolean = true,
    val isTtsSaving: Boolean = false,
    val previewingSpeakerCode: String? = null,
    val speakers: List<TtsSpeakerOption> = emptyList(),
    val selectedSpeakerCode: String? = null,
    val errorMessage: String? = null,
    val eventMessage: String? = null,
)

@HiltViewModel
class AppSettingsViewModel
    @Inject
    constructor(
        private val appThemeModeRepository: AppThemeModeRepository,
        private val accountRepository: AccountRepository,
        private val translateRepository: TranslateRepository,
        private val audioPlayer: AudioPlayer,
    ) : ViewModel() {
        val themeMode: StateFlow<AppThemeMode> =
            appThemeModeRepository.themeMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STATE_SUBSCRIPTION_TIMEOUT_MS),
                initialValue = AppThemeMode.System,
            )

        private val _uiState = MutableStateFlow(AppSettingsUiState())
        val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

        private var previewJob: Job? = null

        init {
            loadTtsSpeakerSettings()
        }

        fun updateThemeMode(mode: AppThemeMode) {
            viewModelScope.launch {
                appThemeModeRepository.saveThemeMode(mode)
            }
        }

        fun loadTtsSpeakerSettings() {
            if (_uiState.value.isTtsSaving) return

            _uiState.value =
                _uiState.value.copy(
                    isTtsLoading = true,
                    errorMessage = null,
                )

            viewModelScope.launch {
                try {
                    val accountInfo =
                        withContext(Dispatchers.IO) {
                            accountRepository.getAccountInfo()
                        }
                    val speakers =
                        withContext(Dispatchers.IO) {
                            accountRepository.getTtsSpeakerOptions()
                        }

                    _uiState.value =
                        AppSettingsUiState(
                            isTtsLoading = false,
                            speakers = speakers,
                            selectedSpeakerCode = accountInfo.ttsSpeaker,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Log.e(TAG, "Failed to load TTS speaker settings", e)
                    _uiState.value =
                        _uiState.value.copy(
                            isTtsLoading = false,
                            errorMessage = e.message ?: DEFAULT_TTS_LOAD_ERROR_MESSAGE,
                        )
                }
            }
        }

        fun updateTtsSpeaker(code: String) {
            val currentState = _uiState.value
            if (currentState.isTtsLoading || currentState.isTtsSaving) return

            if (currentState.selectedSpeakerCode == code) {
                playSpeakerPreview(
                    code = code,
                    savedSpeaker = false,
                )
                return
            }

            _uiState.value =
                currentState.copy(
                    isTtsSaving = true,
                    errorMessage = null,
                    eventMessage = null,
                )

            viewModelScope.launch {
                try {
                    val updateResult =
                        withContext(Dispatchers.IO) {
                            accountRepository.updateTtsSpeaker(code)
                        }
                    _uiState.value =
                        _uiState.value.copy(
                            isTtsLoading = false,
                            isTtsSaving = false,
                            selectedSpeakerCode = updateResult.ttsSpeaker,
                            eventMessage = null,
                        )
                    playSpeakerPreview(
                        code = updateResult.ttsSpeaker,
                        savedSpeaker = true,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Log.e(TAG, "Failed to update TTS speaker", e)
                    _uiState.value =
                        _uiState.value.copy(
                            isTtsSaving = false,
                            errorMessage = e.message ?: DEFAULT_TTS_SAVE_ERROR_MESSAGE,
                        )
                }
            }
        }

        fun clearEventMessage() {
            if (_uiState.value.eventMessage != null) {
                _uiState.value = _uiState.value.copy(eventMessage = null)
            }
        }

        override fun onCleared() {
            previewJob?.cancel()
            audioPlayer.stop()
            super.onCleared()
        }

        private fun playSpeakerPreview(
            code: String,
            savedSpeaker: Boolean,
        ) {
            previewJob?.cancel()
            audioPlayer.stop()
            _uiState.value =
                _uiState.value.copy(
                    previewingSpeakerCode = code,
                    eventMessage = null,
                )

            previewJob =
                viewModelScope.launch {
                    val previewResult =
                        withContext(Dispatchers.IO) {
                            translateRepository.translateSignToSpeech(
                                words = TTS_PREVIEW_SAMPLE_WORDS,
                                sessionId = null,
                            )
                        }

                    previewResult
                        .onSuccess { response ->
                            val audioBase64 = response.audioBase64.orEmpty()
                            if (audioBase64.isBlank()) {
                                finishPreview(
                                    code = code,
                                    message = previewFailureMessage(savedSpeaker),
                                )
                            } else {
                                audioPlayer.playBase64(
                                    base64Data = audioBase64,
                                    onComplete = { finishPreview(code = code) },
                                    onError = {
                                        finishPreview(
                                            code = code,
                                            message = previewFailureMessage(savedSpeaker),
                                        )
                                    },
                                )
                            }
                        }.onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            finishPreview(
                                code = code,
                                message = previewFailureMessage(savedSpeaker),
                            )
                        }
                }
        }

        private fun finishPreview(
            code: String,
            message: String? = null,
        ) {
            val currentState = _uiState.value
            if (currentState.previewingSpeakerCode != code) return

            _uiState.value =
                currentState.copy(
                    previewingSpeakerCode = null,
                    eventMessage = message,
                )
        }

        private fun previewFailureMessage(savedSpeaker: Boolean): String =
            if (savedSpeaker) {
                TTS_PREVIEW_FAILED_AFTER_SAVE_MESSAGE
            } else {
                TTS_PREVIEW_FAILED_MESSAGE
            }
    }

private const val STATE_SUBSCRIPTION_TIMEOUT_MS = 5_000L
private const val DEFAULT_TTS_LOAD_ERROR_MESSAGE = "목소리 설정을 불러오지 못했어요."
private const val DEFAULT_TTS_SAVE_ERROR_MESSAGE = "목소리 설정을 저장하지 못했어요."
private const val TTS_PREVIEW_FAILED_MESSAGE = "샘플 음성을 재생하지 못했어요."
private const val TTS_PREVIEW_FAILED_AFTER_SAVE_MESSAGE = "목소리는 바뀌었지만 샘플 음성은 재생하지 못했어요."
private val TTS_PREVIEW_SAMPLE_WORDS = listOf("안녕", "반갑다")
private const val TAG = "AppSettingsViewModel"
