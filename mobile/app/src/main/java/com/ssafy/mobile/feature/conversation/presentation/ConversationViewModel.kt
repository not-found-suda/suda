package com.ssafy.mobile.feature.conversation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.audio.AudioPlayer
import com.ssafy.mobile.core.audio.TtsPlayer
import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.network.NetworkMonitor
import com.ssafy.mobile.core.stt.SttEngine
import com.ssafy.mobile.core.stt.SttEvent
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.MessageStatus
import com.ssafy.mobile.feature.conversation.domain.model.SenderType
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        private val ttsPlayer: TtsPlayer,
        private val sttEngine: SttEngine,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _sessionState = MutableStateFlow(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private val _isOnline = MutableStateFlow(true)
        val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

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
            // 네트워크 상태 모니터링
            viewModelScope.launch {
                networkMonitor.isOnline.collect { online ->
                    _isOnline.value = online
                }
            }

            viewModelScope.launch {
                signRecognitionEngine.events.collect { event ->
                    if (_sessionState.value == SessionState.Active) {
                        handleEvent(event)
                    }
                }
            }
        }

        private fun handleEvent(event: SignRecognitionEvent) {
            if (event !is SignRecognitionEvent.Prediction) return

            val currentGlosses = _lastGlosses.value + event.gloss
            _lastGlosses.value = currentGlosses

            // 타이머 재시작 로직 인라인화
            completionTimerJob?.cancel()
            completionTimerJob =
                viewModelScope.launch {
                    kotlinx.coroutines.delay(COMPLETION_THRESHOLD_MS)
                    if (currentGlosses.isNotEmpty()) {
                        requestTranslation(currentGlosses)
                    }
                }
        }

        private fun requestTranslation(words: List<String>) {
            translationJob?.cancel()
            if (_isOnline.value) {
                // [온라인 모드] 기존 클라우드 번역 로직
                performCloudTranslation(words)
            } else {
                // [오프라인 모드] Fallback 로직
                performOfflineFallback(words)
            }
        }

        private fun performCloudTranslation(words: List<String>) {
            // 0. 교정 중인 부모 메시지 추가
            addOrUpdateMessage(
                text = "교정 중...",
                isFinal = false,
                senderType = SenderType.PARENT,
            )

            translationJob =
                viewModelScope.launch {
                    translateRepository
                        .translateSignToSpeech(words)
                        .onSuccess { response ->
                            _translatedText.value = response.correctedText
                            addOrUpdateMessage(
                                text = response.correctedText,
                                isFinal = true,
                                senderType = SenderType.PARENT,
                            )

                            response.audioBase64?.let { base64Data ->
                                sttEngine.stopListening()
                                audioPlayer.playBase64(
                                    base64Data = base64Data,
                                    onComplete = {
                                        if (_sessionState.value == SessionState.Active) {
                                            sttEngine.startListening()
                                        }
                                    },
                                    onError = {
                                        if (_sessionState.value == SessionState.Active) {
                                            sttEngine.startListening()
                                        }
                                    },
                                )
                            } ?: run {
                                if (_sessionState.value == SessionState.Active) {
                                    sttEngine.startListening()
                                }
                            }

                            _lastGlosses.value = emptyList()
                        }.onFailure {
                            addOrUpdateMessage(
                                text = "번역에 실패했습니다. 다시 시도해 주세요.",
                                isFinal = true,
                                senderType = SenderType.PARENT,
                            )
                            if (_sessionState.value == SessionState.Active) {
                                sttEngine.startListening()
                            }
                        }
                }
        }

        private fun performOfflineFallback(words: List<String>) {
            val fallbackText = words.joinToString(" ")
            _translatedText.value = fallbackText

            // 오프라인이므로 즉시 최종 메시지로 추가
            addOrUpdateMessage(
                text = fallbackText,
                isFinal = true,
                senderType = SenderType.PARENT,
            )

            // 시스템 TTS로 재생
            sttEngine.stopListening()
            ttsPlayer.speak(
                text = fallbackText,
                onComplete = {
                    if (_sessionState.value == SessionState.Active) {
                        sttEngine.startListening()
                    }
                },
                onError = {
                    if (_sessionState.value == SessionState.Active) {
                        sttEngine.startListening()
                    }
                },
            )

            _lastGlosses.value = emptyList()
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
                            is SttEvent.PartialResults ->
                                updateOrAddChildMessage(
                                    event.text,
                                    isFinal = false,
                                )
                            is SttEvent.Results ->
                                updateOrAddChildMessage(
                                    event.text,
                                    isFinal = true,
                                )
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
            _messages.value = emptyList()
            translationJob?.cancel()
            completionTimerJob?.cancel()
            sttJob?.cancel()
            audioPlayer.stop()
            ttsPlayer.stop()
        }

        private fun addOrUpdateMessage(
            text: String,
            isFinal: Boolean,
            senderType: SenderType,
        ) {
            _messages.update { currentList ->
                val lastMessage = currentList.lastOrNull()
                if (lastMessage != null &&
                    lastMessage.senderType == senderType &&
                    lastMessage.status == MessageStatus.PENDING
                ) {
                    currentList.dropLast(1) +
                        lastMessage.copy(
                            text = text,
                            status =
                                if (isFinal) {
                                    MessageStatus.COMPLETED
                                } else {
                                    MessageStatus.PENDING
                                },
                        )
                } else {
                    currentList +
                        ChatMessage(
                            text = text,
                            senderType = senderType,
                            status =
                                if (isFinal) {
                                    MessageStatus.COMPLETED
                                } else {
                                    MessageStatus.PENDING
                                },
                        )
                }
            }
        }

        private fun updateOrAddChildMessage(
            text: String,
            isFinal: Boolean,
        ) {
            _sttText.value = text
            addOrUpdateMessage(text, isFinal, SenderType.CHILD)
        }

        override fun onCleared() {
            super.onCleared()
            audioPlayer.stop()
            ttsPlayer.stop()
            sttEngine.stopListening()
        }

        fun onLandmarkFrame(frame: LandmarkFrameResult) {
            if (_sessionState.value != SessionState.Active) return

            signRecognitionEngine.submitFrame(frame)
        }

        companion object {
            private const val COMPLETION_THRESHOLD_MS = 2000L
        }
    }
