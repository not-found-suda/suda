package com.ssafy.mobile.feature.conversation.presentation

import android.util.Log
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
import com.ssafy.mobile.feature.conversation.domain.model.TranslationMode
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import com.ssafy.mobile.feature.conversation.domain.repository.TranslationModeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SessionState {
    Idle,
    Active,
}

enum class ConversationPhase {
    Idle,
    Preparing,
    RecognizingSign,
    NoHandsDetected,
    CollectingSigns,
    Translating,
    Speaking,
    ListeningSpeech,
    AnalyzingSpeech,
    Fallback,
    Error,
}

@Suppress("LargeClass", "LongParameterList", "TooManyFunctions")
@HiltViewModel
class ConversationViewModel
    @Inject
    constructor(
        private val signRecognitionEngine: SignRecognitionEngine,
        private val translateRepository: TranslateRepository,
        private val translationModeRepository: TranslationModeRepository,
        private val audioPlayer: AudioPlayer,
        private val ttsPlayer: TtsPlayer,
        private val sttEngine: SttEngine,
        private val networkMonitor: NetworkMonitor,
        private val androidAudioRecorder: AndroidAudioRecorder,
    ) : ViewModel() {
        private val _sessionState = MutableStateFlow(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private val _conversationPhase = MutableStateFlow(ConversationPhase.Idle)
        val conversationPhase: StateFlow<ConversationPhase> = _conversationPhase.asStateFlow()

        private val _isOnline = MutableStateFlow(true)
        val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

        private val _translationMode = MutableStateFlow(TranslationMode.DEFAULT)
        val translationMode: StateFlow<TranslationMode> = _translationMode.asStateFlow()

        private val _translationModeNotice = MutableStateFlow<String?>(null)
        val translationModeNotice: StateFlow<String?> = _translationModeNotice.asStateFlow()

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
        private var cloudSttJob: Job? = null
        private var currentSttSessionId = 0
        private var isResultsReceived = false
        private var isStoppedReceived = false
        private var isCloudSttFallbackActive = false
        private var cloudSttFailureCount = 0

        init {
            viewModelScope.launch {
                translationModeRepository.translationMode.collect { mode ->
                    val previousMode = _translationMode.value
                    _translationMode.value = mode
                    if (previousMode != mode) {
                        resetCloudSttFallback()
                        _translationModeNotice.value = null
                        if (_sessionState.value == SessionState.Active && !isTtsPlaying) {
                            stopRecordingForStt()
                            startRecordingForStt()
                        }
                    }
                }
            }

            // 네트워크 상태 모니터링
            viewModelScope.launch {
                networkMonitor.isOnline.collect { online ->
                    val wasOnline = _isOnline.value
                    _isOnline.value = online
                    if (
                        wasOnline != online &&
                        _sessionState.value == SessionState.Active &&
                        !isTtsPlaying
                    ) {
                        if (online) {
                            resetCloudSttFallback()
                        }
                        updateNetworkFallbackNotice(online)
                        stopRecordingForStt()
                        startRecordingForStt()
                    }
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
            when (event) {
                SignRecognitionEvent.ModelLoading ->
                    _conversationPhase.value = ConversationPhase.Preparing
                SignRecognitionEvent.Ready,
                SignRecognitionEvent.Started,
                -> _conversationPhase.value = ConversationPhase.RecognizingSign
                SignRecognitionEvent.NoHandsDetected -> handleNoHandsDetected()
                is SignRecognitionEvent.Prediction -> handlePrediction(event)
                is SignRecognitionEvent.Utterance -> handleUtterance(event)
                is SignRecognitionEvent.Error -> handleSignRecognitionError(event)
                is SignRecognitionEvent.Metrics,
                SignRecognitionEvent.Stopped,
                -> Unit
            }
        }

        private fun handlePrediction(event: SignRecognitionEvent.Prediction) {
            val currentGlosses = _lastGlosses.value + event.gloss
            _lastGlosses.value = currentGlosses
            _conversationPhase.value = ConversationPhase.CollectingSigns

            restartTranslationTimer(currentGlosses)
        }

        private fun handleUtterance(event: SignRecognitionEvent.Utterance) {
            if (event.glosses.isEmpty()) return

            _lastGlosses.value = event.glosses
            _conversationPhase.value = ConversationPhase.CollectingSigns
            restartTranslationTimer(event.glosses)
        }

        private fun handleNoHandsDetected() {
            if (_lastGlosses.value.isEmpty()) {
                _conversationPhase.value = ConversationPhase.NoHandsDetected
            }
        }

        private fun handleSignRecognitionError(event: SignRecognitionEvent.Error) {
            _conversationPhase.value = ConversationPhase.Error
            addSystemMessage(event.message)
        }

        private fun restartTranslationTimer(glosses: List<String>) {
            completionTimerJob?.cancel()
            completionTimerJob =
                viewModelScope.launch {
                    delay(COMPLETION_THRESHOLD_MS)
                    if (glosses.isNotEmpty()) {
                        requestTranslation(glosses)
                    }
                }
        }

        private fun requestTranslation(words: List<String>) {
            translationJob?.cancel()
            _conversationPhase.value = ConversationPhase.Translating
            when (_translationMode.value) {
                TranslationMode.AUTO ->
                    if (_isOnline.value) {
                        performCloudTranslation(
                            words = words,
                            fallbackOnFailure = true,
                        )
                    } else {
                        performOnDeviceTranslation(
                            words = words,
                            notice = NOTICE_AUTO_OFFLINE_FALLBACK,
                        )
                    }

                TranslationMode.SERVER ->
                    if (_isOnline.value) {
                        performCloudTranslation(
                            words = words,
                            fallbackOnFailure = false,
                        )
                    } else {
                        showServerOnlyUnavailableMessage()
                    }

                TranslationMode.ON_DEVICE -> performOnDeviceTranslation(words = words)
            }
        }

        private fun performCloudTranslation(
            words: List<String>,
            fallbackOnFailure: Boolean,
        ) {
            _conversationPhase.value = ConversationPhase.Translating

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
                            _translationModeNotice.value = null
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
                        }.onFailure { throwable ->
                            if (fallbackOnFailure) {
                                Log.w(
                                    TAG,
                                    "Cloud translation failed. fallback to on-device.",
                                    throwable,
                                )
                                performOnDeviceTranslation(
                                    words = words,
                                    notice = NOTICE_AUTO_SERVER_FALLBACK,
                                )
                            } else {
                                addOrUpdateMessage(
                                    text = "서버 번역에 실패했습니다. 다시 시도해 주세요.",
                                    isFinal = true,
                                    senderType = SenderType.PARENT,
                                )
                                _lastGlosses.value = emptyList()
                                startRecordingForStt()
                            }
                        }
                }
        }

        private fun performOnDeviceTranslation(
            words: List<String>,
            notice: String? = null,
        ) {
            notice?.let { _translationModeNotice.value = it }
            _conversationPhase.value =
                if (notice == null) {
                    ConversationPhase.Translating
                } else {
                    ConversationPhase.Fallback
                }
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
            _conversationPhase.value = ConversationPhase.Speaking
            ttsPlayer.speak(
                text = fallbackText,
                onComplete = { resumeListeningAfterTts() },
                onError = { resumeListeningAfterTts() },
            )

            _lastGlosses.value = emptyList()
        }

        private fun startRecordingForStt() {
            if (_sessionState.value != SessionState.Active || isTtsPlaying) return

            _conversationPhase.value = ConversationPhase.ListeningSpeech

            // 새 세션 시작 시 상태 초기화 (오염 방지)
            isResultsReceived = false
            isStoppedReceived = false

            when {
                shouldUseCloudStt() -> startCloudRecordingLoop()
                shouldUseLocalStt() -> startLocalSttListening()
                else -> showServerOnlyUnavailableMessage()
            }
        }

        private fun startLocalSttListening() {
            cloudSttJob?.cancel()
            cloudSttJob = null
            androidAudioRecorder.stop()
            _conversationPhase.value = ConversationPhase.ListeningSpeech
            currentSttSessionId = sttEngine.nextSessionId()
            sttEngine.startListening(currentSttSessionId)
        }

        private fun startCloudRecordingLoop() {
            if (cloudSttJob?.isActive == true) return

            sttEngine.stopListening()
            _conversationPhase.value = ConversationPhase.ListeningSpeech
            currentSttSessionId = sttEngine.nextSessionId()

            cloudSttJob =
                viewModelScope.launch {
                    while (isActive && canUseCloudStt()) {
                        val fileName = "stt_audio_$currentSttSessionId"
                        val started = androidAudioRecorder.start(fileName)
                        val nextDelayMs =
                            if (!started) {
                                Log.w(TAG, "Cloud STT recorder start failed")
                                CLOUD_STT_RETRY_DELAY_MS
                            } else {
                                Log.d(TAG, "Cloud STT recording started: $fileName")
                                val audioFile = waitForCloudSpeechFile()
                                if (audioFile != null && canUseCloudStt()) {
                                    _conversationPhase.value = ConversationPhase.AnalyzingSpeech
                                    updateOrAddChildMessage(
                                        text = CLOUD_STT_ANALYZING_MESSAGE,
                                        isFinal = false,
                                    )
                                    performCloudStt(audioFile)
                                } else {
                                    CLOUD_STT_RETRY_DELAY_MS
                                }
                            }
                        if (canUseCloudStt()) {
                            delay(nextDelayMs)
                        }
                    }
                    if (canStartLocalSttAfterCloudLoop()) {
                        startLocalSttFromCloudLoop()
                    }
                }
        }

        private suspend fun waitForCloudSpeechFile(): File? {
            val startedAt = System.currentTimeMillis()
            var speechDetected = false
            var lastVoiceAt = startedAt
            var peakAmplitude = 0
            var stopReason = CLOUD_STT_STOP_REASON_CANCELLED
            var voiceFrameCount = 0
            var consecutiveVoiceFrameCount = 0
            var maxConsecutiveVoiceFrameCount = 0

            while (currentCoroutineContext().isActive && canUseCloudStt()) {
                delay(CLOUD_STT_POLL_INTERVAL_MS)

                val amplitude = androidAudioRecorder.getMaxAmplitude()
                peakAmplitude = maxOf(peakAmplitude, amplitude)
                _micVolume.value = amplitude.toFloat()

                val now = System.currentTimeMillis()
                val recordingDuration = now - startedAt
                if (amplitude >= CLOUD_STT_VOICE_THRESHOLD) {
                    voiceFrameCount++
                    consecutiveVoiceFrameCount++
                    maxConsecutiveVoiceFrameCount =
                        maxOf(maxConsecutiveVoiceFrameCount, consecutiveVoiceFrameCount)
                    speechDetected = voiceFrameCount >= CLOUD_STT_MIN_VOICE_FRAME_COUNT
                    lastVoiceAt = now
                } else {
                    consecutiveVoiceFrameCount = 0
                }

                val currentStopReason =
                    getCloudRecordingStopReason(
                        speechDetected,
                        recordingDuration,
                        now - lastVoiceAt,
                    )
                if (currentStopReason != null) {
                    stopReason = currentStopReason
                    break
                }
            }

            val file = androidAudioRecorder.stop()
            val recordingDuration = System.currentTimeMillis() - startedAt
            Log.d(
                TAG,
                "Cloud STT recording stopped: path=${file?.absolutePath}, " +
                    "size=${file?.length()}, speechDetected=$speechDetected, " +
                    "peakAmplitude=$peakAmplitude, duration=$recordingDuration, " +
                    "reason=$stopReason, voiceFrames=$voiceFrameCount, " +
                    "maxConsecutiveVoiceFrames=$maxConsecutiveVoiceFrameCount",
            )

            val shouldUpload =
                file?.let {
                    shouldUploadCloudAudioFile(
                        file = it,
                        speechDetected = speechDetected,
                        peakAmplitude = peakAmplitude,
                        recordingDuration = recordingDuration,
                        voiceFrameCount = voiceFrameCount,
                    )
                } == true
            return if (shouldUpload) {
                file
            } else {
                Log.d(
                    TAG,
                    "Cloud STT recording discarded: speechDetected=$speechDetected, " +
                        "peakAmplitude=$peakAmplitude, duration=$recordingDuration, " +
                        "voiceFrames=$voiceFrameCount, size=${file?.length()}",
                )
                file?.let(::deleteCloudSttAudioFile)
                null
            }
        }

        private fun shouldUseCloudStt(): Boolean =
            when (_translationMode.value) {
                TranslationMode.AUTO -> _isOnline.value && !isCloudSttFallbackActive
                TranslationMode.SERVER -> _isOnline.value
                TranslationMode.ON_DEVICE -> false
            }

        private fun shouldUseLocalStt(): Boolean =
            when (_translationMode.value) {
                TranslationMode.AUTO -> !_isOnline.value || isCloudSttFallbackActive
                TranslationMode.SERVER -> false
                TranslationMode.ON_DEVICE -> true
            }

        private fun canUseCloudStt(): Boolean =
            _sessionState.value == SessionState.Active && shouldUseCloudStt() && !isTtsPlaying

        private fun canHandleLocalSttEvent(): Boolean =
            _sessionState.value == SessionState.Active && shouldUseLocalStt() && !isTtsPlaying

        private fun canStartLocalSttAfterCloudLoop(): Boolean =
            _sessionState.value == SessionState.Active && shouldUseLocalStt() && !isTtsPlaying

        private fun startLocalSttFromCloudLoop() {
            cloudSttJob = null
            androidAudioRecorder.stop()
            _conversationPhase.value = ConversationPhase.ListeningSpeech
            currentSttSessionId = sttEngine.nextSessionId()
            sttEngine.startListening(currentSttSessionId)
        }

        private fun getCloudRecordingStopReason(
            speechDetected: Boolean,
            recordingDuration: Long,
            silenceDuration: Long,
        ): String? =
            when {
                speechDetected &&
                    recordingDuration >= CLOUD_STT_MIN_RECORDING_MS &&
                    silenceDuration >= CLOUD_STT_SILENCE_TIMEOUT_MS ->
                    CLOUD_STT_STOP_REASON_SILENCE
                recordingDuration >= CLOUD_STT_MAX_RECORDING_MS ->
                    CLOUD_STT_STOP_REASON_MAX_DURATION
                !speechDetected &&
                    recordingDuration >= CLOUD_STT_NO_SPEECH_TIMEOUT_MS ->
                    CLOUD_STT_STOP_REASON_NO_SPEECH
                else -> null
            }

        private fun isValidCloudAudioFile(file: File): Boolean =
            file.exists() && file.length() >= CLOUD_STT_MIN_FILE_BYTES

        private fun shouldUploadCloudAudioFile(
            file: File,
            speechDetected: Boolean,
            peakAmplitude: Int,
            recordingDuration: Long,
            voiceFrameCount: Int,
        ): Boolean =
            isValidCloudAudioFile(file) &&
                recordingDuration >= CLOUD_STT_MIN_UPLOAD_RECORDING_MS &&
                voiceFrameCount >= CLOUD_STT_MIN_VOICE_FRAME_COUNT &&
                (speechDetected || peakAmplitude >= CLOUD_STT_FALLBACK_VOICE_THRESHOLD)

        private suspend fun checkAndRestartStt() {
            if (isResultsReceived && isStoppedReceived) {
                // 잦은 재시작으로 인한 BUSY 에러 방지
                kotlinx.coroutines.delay(STT_RESTART_DELAY_MS)
                startRecordingForStt()
            }
        }

        private fun stopRecordingForStt(): File? {
            cloudSttJob?.cancel()
            cloudSttJob = null
            sttEngine.stopListening()
            val file = androidAudioRecorder.stop()
            file?.let(::deleteCloudSttAudioFile)
            return file
        }

        fun startSession() {
            _sessionState.value = SessionState.Active
            _conversationPhase.value = ConversationPhase.Preparing
            resetCloudSttFallback()
            signRecognitionEngine.start()

            // 1. 기존 STT 수집이 있다면 취소
            sttJob?.cancel()

            // 2. STT 수집 시작
            sttJob =
                viewModelScope.launch {
                    sttEngine.events.collect { event ->
                        if (event.sessionId != currentSttSessionId || !canHandleLocalSttEvent()) {
                            return@collect
                        }

                        when (event) {
                            is SttEvent.PartialResults -> {
                                isResultsReceived = false
                                isStoppedReceived = false
                                _conversationPhase.value = ConversationPhase.ListeningSpeech
                                updateOrAddChildMessage(
                                    event.text,
                                    isFinal = false,
                                )
                            }
                            is SttEvent.Results -> {
                                _conversationPhase.value = ConversationPhase.ListeningSpeech
                                updateOrAddChildMessage(
                                    event.text,
                                    isFinal = true,
                                )
                                isResultsReceived = true
                                checkAndRestartStt()
                            }
                            is SttEvent.EndOfSpeech -> {
                                _conversationPhase.value = ConversationPhase.AnalyzingSpeech
                            }
                            is SttEvent.Stopped -> {
                                if (_sessionState.value == SessionState.Active && !isTtsPlaying) {
                                    isStoppedReceived = true
                                    if (shouldUseLocalStt()) {
                                        isResultsReceived = true // 오프라인은 결과 대기 불필요
                                    }
                                    checkAndRestartStt()
                                }
                            }
                            is SttEvent.VolumeChanged -> _micVolume.value = event.db
                            is SttEvent.Error -> {
                                Log.w(TAG, "Local STT error: ${event.message}")
                                _conversationPhase.value = ConversationPhase.Error
                                isResultsReceived = true
                                isStoppedReceived = false
                                addSystemMessage("음성 인식에 실패했습니다. 다시 말해 주세요.")
                            }
                            else -> {}
                        }
                    }
                }
            startRecordingForStt()
        }

        private suspend fun performCloudStt(audioFile: File): Long {
            var restartDelayMs = STT_RESTART_DELAY_MS
            try {
                Log.d(
                    TAG,
                    "Cloud STT upload started: path=${audioFile.absolutePath}, " +
                        "size=${audioFile.length()}, mime=$CLOUD_STT_AUDIO_MIME_TYPE",
                )
                translateRepository
                    .translateSpeechToText(audioFile, CLOUD_STT_AUDIO_MIME_TYPE)
                    .onSuccess { response ->
                        cloudSttFailureCount = 0
                        _conversationPhase.value = ConversationPhase.ListeningSpeech
                        val displayText =
                            buildCloudSttDisplayText(
                                recognizedText = response.recognizedText,
                                correctedText = response.correctedText,
                                corrected = response.corrected,
                            )
                        if (canUseCloudStt()) {
                            updateOrAddChildMessage(displayText, isFinal = true)
                            _sttText.value = ""
                        }
                        Log.d(
                            TAG,
                            "Cloud STT upload succeeded: recognized=${response.recognizedText}, " +
                                "corrected=${response.correctedText}, isCorrected=${response.corrected}",
                        )
                    }.onFailure {
                        cloudSttFailureCount++
                        restartDelayMs = getCloudSttFailureRetryDelay()
                        if (shouldFallbackToLocalStt()) {
                            isCloudSttFallbackActive = true
                            _conversationPhase.value = ConversationPhase.Fallback
                            _translationModeNotice.value = NOTICE_AUTO_STT_FALLBACK
                            restartDelayMs = STT_RESTART_DELAY_MS
                        } else {
                            _conversationPhase.value = ConversationPhase.Error
                        }
                        Log.w(
                            TAG,
                            "Cloud STT upload failed: failureCount=$cloudSttFailureCount, " +
                                "retryDelayMs=$restartDelayMs",
                            it,
                        )
                        if (canUseCloudStt()) {
                            updateOrAddChildMessage(
                                text = CLOUD_STT_FAILURE_MESSAGE,
                                isFinal = true,
                            )
                        }
                    }
            } finally {
                deleteCloudSttAudioFile(audioFile)
            }
            return restartDelayMs
        }

        private fun deleteCloudSttAudioFile(audioFile: File) {
            if (audioFile.exists() && !audioFile.delete()) {
                Log.d(TAG, "Cloud STT temp file delete failed: path=${audioFile.absolutePath}")
            }
        }

        private fun getCloudSttFailureRetryDelay(): Long =
            minOf(
                CLOUD_STT_FAILURE_RETRY_DELAY_MS * cloudSttFailureCount,
                CLOUD_STT_MAX_FAILURE_RETRY_DELAY_MS,
            )

        private fun buildCloudSttDisplayText(
            recognizedText: String,
            correctedText: String,
            corrected: Boolean,
        ): String {
            val normalizedRecognized = recognizedText.trim()
            val normalizedCorrected = correctedText.trim()
            return if (
                corrected &&
                normalizedCorrected.isNotBlank() &&
                normalizedRecognized != normalizedCorrected
            ) {
                "원문: $normalizedRecognized\n수정: $normalizedCorrected"
            } else {
                normalizedRecognized.ifBlank { normalizedCorrected }
            }
        }

        private fun handleTtsSuccess(base64Audio: String) {
            stopRecordingForStt()
            isTtsPlaying = true
            _conversationPhase.value = ConversationPhase.Speaking
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

        fun updateTranslationMode(mode: TranslationMode) {
            viewModelScope.launch {
                translationModeRepository.saveTranslationMode(mode)
            }
        }

        fun stopSession() {
            _sessionState.value = SessionState.Idle
            _conversationPhase.value = ConversationPhase.Idle
            signRecognitionEngine.stop()
            stopRecordingForStt()
            isResultsReceived = false
            isStoppedReceived = false
            _lastGlosses.value = emptyList()
            _translatedText.value = ""
            _translationModeNotice.value = null
            _sttText.value = ""
            _micVolume.value = 0f
            _messages.value = emptyList()
            resetCloudSttFallback()
            translationJob?.cancel()
            completionTimerJob?.cancel()
            sttJob?.cancel()
            cloudSttJob?.cancel()
            cloudSttJob = null
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

        private fun addSystemMessage(text: String) {
            if (isSameAsLastSystemMessage(text)) return

            addOrUpdateMessage(
                text = text,
                isFinal = true,
                senderType = SenderType.SYSTEM,
            )
        }

        private fun isSameAsLastSystemMessage(text: String): Boolean {
            val lastMessage = _messages.value.lastOrNull()
            return lastMessage?.senderType == SenderType.SYSTEM && lastMessage.text == text
        }

        private fun updateNetworkFallbackNotice(isOnline: Boolean) {
            _translationModeNotice.value =
                when {
                    _translationMode.value == TranslationMode.AUTO && !isOnline ->
                        NOTICE_AUTO_OFFLINE_FALLBACK
                    _translationMode.value == TranslationMode.SERVER && !isOnline ->
                        NOTICE_SERVER_OFFLINE
                    else -> null
                }
        }

        private fun shouldFallbackToLocalStt(): Boolean =
            _translationMode.value == TranslationMode.AUTO &&
                cloudSttFailureCount >= AUTO_CLOUD_STT_FALLBACK_FAILURE_COUNT

        private fun resetCloudSttFallback() {
            isCloudSttFallbackActive = false
            cloudSttFailureCount = 0
        }

        private fun showServerOnlyUnavailableMessage() {
            _conversationPhase.value = ConversationPhase.Error
            _translationModeNotice.value = NOTICE_SERVER_OFFLINE
            addOrUpdateMessage(
                text = "서버 모드에서는 네트워크 연결이 필요합니다.",
                isFinal = true,
                senderType = SenderType.PARENT,
            )
            _lastGlosses.value = emptyList()
        }

        override fun onCleared() {
            signRecognitionEngine.stop()
            audioPlayer.stop()
            ttsPlayer.stop()
            stopRecordingForStt()
            translationJob?.cancel()
            completionTimerJob?.cancel()
            sttJob?.cancel()
            cloudSttJob?.cancel()
            super.onCleared()
        }

        fun onLandmarkFrame(frame: LandmarkFrameResult) {
            if (_sessionState.value != SessionState.Active) return

            signRecognitionEngine.submitFrame(frame)
        }

        companion object {
            private const val TAG = "ConversationViewModel"
            private const val COMPLETION_THRESHOLD_MS = 2000L
            private const val STT_RESTART_DELAY_MS = 500L
            private const val CLOUD_STT_POLL_INTERVAL_MS = 150L
            private const val CLOUD_STT_MIN_RECORDING_MS = 500L
            private const val CLOUD_STT_MIN_UPLOAD_RECORDING_MS = 1500L
            private const val CLOUD_STT_SILENCE_TIMEOUT_MS = 500L
            private const val CLOUD_STT_MAX_RECORDING_MS = 2800L
            private const val CLOUD_STT_NO_SPEECH_TIMEOUT_MS = 1200L
            private const val CLOUD_STT_RETRY_DELAY_MS = 500L
            private const val CLOUD_STT_FAILURE_RETRY_DELAY_MS = 1500L
            private const val CLOUD_STT_MAX_FAILURE_RETRY_DELAY_MS = 5000L
            private const val CLOUD_STT_VOICE_THRESHOLD = 5000
            private const val CLOUD_STT_FALLBACK_VOICE_THRESHOLD = 5000
            private const val CLOUD_STT_MIN_VOICE_FRAME_COUNT = 2
            private const val CLOUD_STT_MIN_FILE_BYTES = 1024L
            private const val CLOUD_STT_STOP_REASON_SILENCE = "silence"
            private const val CLOUD_STT_STOP_REASON_MAX_DURATION = "max_duration"
            private const val CLOUD_STT_STOP_REASON_NO_SPEECH = "no_speech"
            private const val CLOUD_STT_STOP_REASON_CANCELLED = "cancelled"
            private const val CLOUD_STT_ANALYZING_MESSAGE = "대화 내용을 분석 중입니다..."
            private const val CLOUD_STT_FAILURE_MESSAGE = "인식에 실패했습니다."
            private const val CLOUD_STT_AUDIO_MIME_TYPE = "audio/wav"
            private const val AUTO_CLOUD_STT_FALLBACK_FAILURE_COUNT = 1
            private const val NOTICE_AUTO_OFFLINE_FALLBACK =
                "자동 모드: 네트워크가 없어 기기 내 처리로 전환했어요."
            private const val NOTICE_AUTO_SERVER_FALLBACK =
                "자동 모드: 서버 처리 실패로 기기 내 처리로 전환했어요."
            private const val NOTICE_AUTO_STT_FALLBACK =
                "자동 모드: 서버 음성 인식 실패로 기기 내 인식으로 전환했어요."
            private const val NOTICE_SERVER_OFFLINE =
                "서버 모드: 네트워크 연결 후 다시 사용할 수 있어요."
        }
    }
