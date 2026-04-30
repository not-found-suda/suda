package com.ssafy.mobile.feature.conversation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.audio.AndroidAudioRecorder
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
import java.io.File
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

@Suppress("TooManyFunctions")
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
        private val androidAudioRecorder: AndroidAudioRecorder,
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

        private var isTtsPlaying = false

        private val _micVolume = MutableStateFlow(0f)
        val micVolume: StateFlow<Float> = _micVolume.asStateFlow()

        private var translationJob: Job? = null
        private var completionTimerJob: Job? = null
        private var sttJob: Job? = null
        private var currentSttSessionId = 0
        private var isResultsReceived = false
        private var isStoppedReceived = false

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

                            response.audioBase64?.let { base64Audio ->
                                handleTtsSuccess(base64Audio)
                            } ?: run {
                                startRecordingForStt()
                            }

                            _lastGlosses.value = emptyList()
                        }.onFailure {
                            addOrUpdateMessage(
                                text = "번역에 실패했습니다. 다시 시도해 주세요.",
                                isFinal = true,
                                senderType = SenderType.PARENT,
                            )
                            startRecordingForStt()
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
            isTtsPlaying = true
            ttsPlayer.speak(
                text = fallbackText,
                onComplete = { resumeListeningAfterTts() },
                onError = { resumeListeningAfterTts() },
            )

            _lastGlosses.value = emptyList()
        }

        private fun startRecordingForStt() {
            if (_sessionState.value != SessionState.Active || isTtsPlaying) return

            // 새 세션 시작 시 상태 초기화 (오염 방지)
            isResultsReceived = false
            isStoppedReceived = false

            if (_isOnline.value) {
                androidAudioRecorder.start()
            }
            currentSttSessionId++
            sttEngine.startListening(currentSttSessionId)
        }

        private suspend fun checkAndRestartStt() {
            if (isResultsReceived && isStoppedReceived) {
                // 잦은 재시작으로 인한 BUSY 에러 방지
                kotlinx.coroutines.delay(STT_RESTART_DELAY_MS)
                startRecordingForStt()
            }
        }

        private fun stopRecordingForStt(): File? {
            sttEngine.stopListening()
            return androidAudioRecorder.stop()
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
                        if (event.sessionId != currentSttSessionId) return@collect

                        when (event) {
                            is SttEvent.PartialResults -> {
                                isResultsReceived = false
                                isStoppedReceived = false
                                updateOrAddChildMessage(
                                    event.text,
                                    isFinal = false,
                                )
                            }
                            is SttEvent.Results -> {
                                if (_isOnline.value) {
                                    val file = androidAudioRecorder.stop()
                                    if (file != null) {
                                        _sttText.value = "대화 내용을 분석 중입니다..."
                                        performCloudStt(file)
                                    } else {
                                        _sttText.value = "" // 유효하지 않은 녹음은 텍스트 초기화
                                    }
                                } else {
                                    updateOrAddChildMessage(
                                        event.text,
                                        isFinal = true,
                                    )
                                }
                                isResultsReceived = true
                                checkAndRestartStt()
                            }
                            is SttEvent.EndOfSpeech -> {
                                // 필요 시 UI 처리 (예: "인식 완료, 처리 중...")
                            }
                            is SttEvent.Stopped -> {
                                if (_sessionState.value == SessionState.Active && !isTtsPlaying) {
                                    isStoppedReceived = true
                                    if (!_isOnline.value) {
                                        isResultsReceived = true // 오프라인은 결과 대기 불필요
                                    }
                                    checkAndRestartStt()
                                }
                            }
                            is SttEvent.VolumeChanged -> _micVolume.value = event.db
                            is SttEvent.Error -> {
                                isResultsReceived = true
                                isStoppedReceived = false
                                // 에러 처리 (필요시 UI 알림 추가)
                            }
                            else -> {}
                        }
                    }
                }
            startRecordingForStt()
        }

        private fun performCloudStt(audioFile: File) {
            viewModelScope.launch {
                translateRepository
                    .translateSpeechToText(audioFile, "audio/mp4")
                    .onSuccess { response ->
                        val displayText =
                            if (response.corrected) {
                                response.correctedText
                            } else {
                                response.recognizedText
                            }
                        updateOrAddChildMessage(displayText, isFinal = true)
                        _sttText.value = ""
                    }.onFailure {
                        _sttText.value = "인식에 실패했습니다."
                    }
            }
        }

        private fun handleTtsSuccess(base64Audio: String) {
            stopRecordingForStt()
            isTtsPlaying = true
            audioPlayer.playBase64(
                base64Data = base64Audio,
                onComplete = { resumeListeningAfterTts() },
                onError = { resumeListeningAfterTts() },
            )
        }

        private fun resumeListeningAfterTts() {
            isTtsPlaying = false
            startRecordingForStt()
        }

        fun stopSession() {
            _sessionState.value = SessionState.Idle
            signRecognitionEngine.stop()
            stopRecordingForStt()
            isResultsReceived = false
            isStoppedReceived = false
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
            stopRecordingForStt()
        }

        fun onLandmarkFrame(frame: LandmarkFrameResult) {
            if (_sessionState.value != SessionState.Active) return

            signRecognitionEngine.submitFrame(frame)
        }

        companion object {
            private const val COMPLETION_THRESHOLD_MS = 2000L
            private const val STT_RESTART_DELAY_MS = 500L
        }
    }
