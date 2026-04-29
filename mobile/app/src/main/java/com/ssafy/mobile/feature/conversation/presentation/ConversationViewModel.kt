package com.ssafy.mobile.feature.conversation.presentation

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.audio.AudioPlayer
import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.stt.SttEngine
import com.ssafy.mobile.core.stt.SttEvent
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SessionState {
    Idle,
    Active,
    Stopping,
}

@HiltViewModel
class ConversationViewModel
    @Inject
    constructor(
        private val signRecognitionEngine: SignRecognitionEngine,
        private val translateRepository: TranslateRepository,
        private val audioPlayer: AudioPlayer,
        private val sttEngine: SttEngine,
    ) : ViewModel() {
        private val _sessionState = MutableStateFlow(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private val _lastGlosses = MutableStateFlow<List<String>>(emptyList())
        val lastGlosses: StateFlow<List<String>> = _lastGlosses.asStateFlow()

        private val _translatedText = MutableStateFlow("")
        val translatedText: StateFlow<String> = _translatedText.asStateFlow()

        private val _sttText = MutableStateFlow("")
        val sttText: StateFlow<String> = _sttText.asStateFlow()

        private val _micVolume = MutableStateFlow(0f)
        val micVolume: StateFlow<Float> = _micVolume.asStateFlow()

        private var translationJob: Job? = null
        private var completionTimerJob: Job? = null
        private var sttJob: Job? = null

        init {
            viewModelScope.launch {
                signRecognitionEngine.events.collect { event ->
                    if (_sessionState.value == SessionState.Active) {
                        handleEvent(event)
                    }
                }
            }
        }

        private fun handleEvent(event: SignRecognitionEvent) {
            when (event) {
                is SignRecognitionEvent.Prediction -> {
                    val currentGlosses = _lastGlosses.value + event.gloss
                    _lastGlosses.value = currentGlosses

                    // 새로운 단어가 들어올 때마다 문장 완성 타이머 초기화 및 재시작
                    restartCompletionTimer(currentGlosses)
                }
                else -> {
                    // 기타 상태 처리
                }
            }
        }

        private fun restartCompletionTimer(words: List<String>) {
            completionTimerJob?.cancel()
            completionTimerJob =
                viewModelScope.launch {
                    kotlinx.coroutines.delay(COMPLETION_THRESHOLD_MS)
                    // 2초간 새로운 단어가 없으면 번역 요청
                    if (words.isNotEmpty()) {
                        requestTranslation(words)
                    }
                }
        }

        private fun requestTranslation(words: List<String>) {
            // 이전 진행 중인 번역 요청이 있다면 취소하여 중복 및 순서 꼬임 방지
            translationJob?.cancel()

            translationJob =
                viewModelScope.launch {
                    translateRepository
                        .translateSignToSpeech(words)
                        .onSuccess { response ->
                            _translatedText.value = response.correctedText

                            // 1. 번역 성공 시 음성 재생 (Base64 데이터가 있는 경우)
                            response.audioBase64?.let { base64Data ->
                                runCatching {
                                    val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    audioPlayer.play(audioBytes)
                                }
                            }

                            // 2. 단어 리스트 초기화
                            _lastGlosses.value = emptyList()
                        }.onFailure {
                            // 향후 사용자에게 번역 실패 알림 제공 로직 추가 예정
                        }
                }
        }

        fun startSession() {
            _sessionState.value = SessionState.Active
            signRecognitionEngine.start()

            // 1. 기존 STT 수집이 있다면 취소
            sttJob?.cancel()

            // 2. STT 수집 시작
            sttJob =
                viewModelScope.launch {
                    sttEngine.events.collect { event ->
                        when (event) {
                            is SttEvent.PartialResults -> _sttText.value = event.text
                            is SttEvent.Results -> _sttText.value = event.text
                            is SttEvent.VolumeChanged -> _micVolume.value = event.db
                            is SttEvent.Error -> {
                                // 에러 처리 (필요시 UI 알림 추가)
                            }
                            else -> {}
                        }
                    }
                }
            sttEngine.startListening()
        }

        fun stopSession() {
            _sessionState.value = SessionState.Idle
            signRecognitionEngine.stop()
            sttEngine.stopListening()
            _lastGlosses.value = emptyList()
            _translatedText.value = ""
            _sttText.value = ""
            _micVolume.value = 0f
            translationJob?.cancel()
            completionTimerJob?.cancel()
            sttJob?.cancel()
            audioPlayer.stop()
        }

        override fun onCleared() {
            super.onCleared()
            audioPlayer.release()
            sttEngine.release()
        }

        fun onLandmarkFrame(frame: LandmarkFrameResult) {
            if (_sessionState.value != SessionState.Active) return

            signRecognitionEngine.submitFrame(frame)
        }

        companion object {
            private const val COMPLETION_THRESHOLD_MS = 2000L
        }
    }
