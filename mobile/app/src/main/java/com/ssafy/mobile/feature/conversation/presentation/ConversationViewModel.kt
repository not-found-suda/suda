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
import com.ssafy.mobile.core.stt.SttErrorType
import com.ssafy.mobile.core.stt.SttEvent
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileManager
import com.ssafy.mobile.feature.childprofile.domain.ActiveChildProfileState
import com.ssafy.mobile.feature.conversation.data.remote.model.SpeechToTextResponse
import com.ssafy.mobile.feature.conversation.data.repository.CommsSessionRepository
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.LocalSignSentenceGenerator
import com.ssafy.mobile.feature.conversation.domain.model.MessageStatus
import com.ssafy.mobile.feature.conversation.domain.model.SenderType
import com.ssafy.mobile.feature.conversation.domain.model.TranslationFeedbackReason
import com.ssafy.mobile.feature.conversation.domain.model.TranslationMode
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import com.ssafy.mobile.feature.conversation.domain.repository.TranslationModeRepository
import com.ssafy.mobile.translation.OnDeviceTranslationEngine
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SessionState {
    Idle,
    Active,
}

enum class SignInputPhase {
    Idle,
    Preparing,
    Recognizing,
    NoHandsDetected,
    Collecting,
    Translating,
    Fallback,
    Error,
}

enum class SpeechInputPhase {
    Idle,
    Listening,
    Analyzing,
    Unrecognized,
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
        private val localSignSentenceGenerator: LocalSignSentenceGenerator,
        private val onDeviceTranslationEngine: OnDeviceTranslationEngine,
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val commsSessionRepository: CommsSessionRepository,
    ) : ViewModel() {
        private val _sessionState = MutableStateFlow(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private val _signInputPhase = MutableStateFlow(SignInputPhase.Idle)
        val signInputPhase: StateFlow<SignInputPhase> = _signInputPhase.asStateFlow()

        private val _speechInputPhase = MutableStateFlow(SpeechInputPhase.Idle)
        val speechInputPhase: StateFlow<SpeechInputPhase> = _speechInputPhase.asStateFlow()

        private val _isOnline = MutableStateFlow(true)
        val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

        private val _translationMode = MutableStateFlow(TranslationMode.DEFAULT)
        val translationMode: StateFlow<TranslationMode> = _translationMode.asStateFlow()

        private val _translationModeNotice = MutableStateFlow<String?>(null)
        val translationModeNotice: StateFlow<String?> = _translationModeNotice.asStateFlow()

        private val _translationFeedbackSubmitState =
            MutableStateFlow<TranslationFeedbackSubmitState>(
                TranslationFeedbackSubmitState.Idle,
            )
        val translationFeedbackSubmitState: StateFlow<TranslationFeedbackSubmitState> =
            _translationFeedbackSubmitState.asStateFlow()

        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _lastGlosses = MutableStateFlow<List<String>>(emptyList())
        val lastGlosses: StateFlow<List<String>> = _lastGlosses.asStateFlow()

        private val _predictionFeedbackToken = MutableStateFlow(0L)
        val predictionFeedbackToken: StateFlow<Long> = _predictionFeedbackToken.asStateFlow()

        private val _translatedText = MutableStateFlow("")
        val translatedText: StateFlow<String> = _translatedText.asStateFlow()

        private val _sttText = MutableStateFlow("")
        val sttText: StateFlow<String> = _sttText.asStateFlow()

        private var lastAppSpeechText = ""
        private var lastAppSpeechStartedAt = 0L

        private val _micVolume = MutableStateFlow(0f)
        val micVolume: StateFlow<Float> = _micVolume.asStateFlow()

        private var translationJob: Job? = null
        private var completionTimerJob: Job? = null
        private var sttJob: Job? = null
        private var cloudSttJob: Job? = null
        private var commsSessionJob: Job? = null
        private val commsSessionMutex = Mutex()
        private var commsSessionId: Long? = null
        private var sessionRuntimeStarted = false
        private var currentSttSessionId = 0
        private var isResultsReceived = false
        private var isStoppedReceived = false
        private var cloudSttFailureCount = 0
        private var pendingFeedbackRequest: TranslationFeedbackRequest? = null
        private val onDeviceSpeechStyle = LocalSignSentenceGenerator.SpeechStyle.Polite

        init {
            viewModelScope.launch {
                translationModeRepository.translationMode.collect { mode ->
                    val previousMode = _translationMode.value
                    _translationMode.value = mode
                    if (mode == TranslationMode.ON_DEVICE) {
                        preloadOnDeviceTranslationEngine()
                    }
                    if (previousMode != mode) {
                        resetCloudSttFailures()
                        _translationModeNotice.value = null
                        if (
                            _sessionState.value == SessionState.Active &&
                            sessionRuntimeStarted
                        ) {
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
                        _sessionState.value == SessionState.Active
                    ) {
                        if (online) {
                            resetCloudSttFailures()
                        }
                        updateNetworkFallbackNotice(online)
                        if (sessionRuntimeStarted) {
                            stopRecordingForStt()
                            startRecordingForStt()
                        }
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
            preloadOnDeviceTranslationEngine()
        }

        private fun handleEvent(event: SignRecognitionEvent) {
            when (event) {
                SignRecognitionEvent.ModelLoading ->
                    _signInputPhase.value = SignInputPhase.Preparing
                SignRecognitionEvent.Ready,
                SignRecognitionEvent.Started,
                -> _signInputPhase.value = SignInputPhase.Recognizing
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
            _signInputPhase.value = SignInputPhase.Collecting
            _predictionFeedbackToken.value = event.timestampMs

            restartTranslationTimer(glosses = currentGlosses)
        }

        private fun handleUtterance(event: SignRecognitionEvent.Utterance) {
            if (event.glosses.isEmpty()) return

            _lastGlosses.value = event.glosses
            _signInputPhase.value = SignInputPhase.Collecting
            restartTranslationTimer(
                glosses = event.glosses,
                sentenceType = event.sentenceType,
            )
        }

        private fun handleNoHandsDetected() {
            if (_lastGlosses.value.isEmpty()) {
                _signInputPhase.value = SignInputPhase.NoHandsDetected
            }
        }

        private fun handleSignRecognitionError(event: SignRecognitionEvent.Error) {
            _signInputPhase.value = SignInputPhase.Error
            addSystemMessage(event.message)
        }

        private fun restartTranslationTimer(
            glosses: List<String>,
            sentenceType: String? = null,
        ) {
            completionTimerJob?.cancel()
            completionTimerJob =
                viewModelScope.launch {
                    delay(COMPLETION_THRESHOLD_MS)
                    if (glosses.isNotEmpty()) {
                        requestTranslation(
                            words = glosses,
                            sentenceType = sentenceType,
                        )
                    }
                }
        }

        private fun requestTranslation(
            words: List<String>,
            sentenceType: String? = null,
        ) {
            translationJob?.cancel()
            _signInputPhase.value = SignInputPhase.Translating
            when (_translationMode.value) {
                TranslationMode.AUTO ->
                    if (_isOnline.value) {
                        performCloudTranslation(
                            words = words,
                            sentenceType = sentenceType,
                            fallbackOnFailure = true,
                        )
                    } else {
                        performOnDeviceTranslation(
                            words = words,
                            sentenceType = sentenceType,
                            notice = NOTICE_AUTO_OFFLINE_FALLBACK,
                        )
                    }

                TranslationMode.SERVER ->
                    if (_isOnline.value) {
                        performCloudTranslation(
                            words = words,
                            sentenceType = sentenceType,
                            fallbackOnFailure = false,
                        )
                    } else {
                        showServerOnlyUnavailableMessage(
                            affectsSign = true,
                            affectsSpeech = false,
                        )
                    }

                TranslationMode.ON_DEVICE ->
                    performOnDeviceTranslation(
                        words = words,
                        sentenceType = sentenceType,
                    )
            }
        }

        private fun performCloudTranslation(
            words: List<String>,
            sentenceType: String?,
            fallbackOnFailure: Boolean,
        ) {
            _signInputPhase.value = SignInputPhase.Translating

            // 0. 교정 중인 부모 메시지 추가
            addOrUpdateMessage(
                text = "교정 중...",
                isFinal = false,
                senderType = SenderType.PARENT,
            )

            translationJob =
                viewModelScope.launch {
                    val sessionId = ensureCommsSessionForServerStorage()
                    val result =
                        translateRepository.translateSignToSpeech(
                            words = words,
                            sessionId = sessionId,
                        )

                    result.fold(
                        onSuccess = { response ->
                            _translationModeNotice.value = null
                            _translatedText.value = response.correctedText
                            addOrUpdateMessage(
                                text = response.correctedText,
                                isFinal = true,
                                senderType = SenderType.PARENT,
                                isFeedbackAvailable = true,
                            )

                            response.audioBase64?.let { base64Audio ->
                                handleTtsSuccess(base64Audio)
                            } ?: run {
                                startRecordingForStt()
                            }

                            _lastGlosses.value = emptyList()
                        },
                        onFailure = { throwable ->
                            handleCloudTranslationFailure(
                                throwable = throwable,
                                fallbackOnFailure = fallbackOnFailure,
                                words = words,
                                sentenceType = sentenceType,
                            )
                        },
                    )
                }
        }

        private fun handleCloudTranslationFailure(
            throwable: Throwable,
            fallbackOnFailure: Boolean,
            words: List<String>,
            sentenceType: String?,
        ) {
            if (fallbackOnFailure) {
                Log.w(
                    TAG,
                    "Cloud translation failed. fallback to on-device.",
                    throwable,
                )
                performOnDeviceTranslation(
                    words = words,
                    sentenceType = sentenceType,
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

        private fun performOnDeviceTranslation(
            words: List<String>,
            sentenceType: String? = null,
            notice: String? = null,
        ) {
            notice?.let { _translationModeNotice.value = it }
            _signInputPhase.value =
                if (notice == null) {
                    SignInputPhase.Translating
                } else {
                    SignInputPhase.Fallback
                }
            translationJob =
                viewModelScope.launch {
                    val fallbackText =
                        resolveOnDeviceTranslation(
                            words = words,
                            sentenceType = sentenceType,
                        )
                    _translatedText.value = fallbackText

                    // 오프라인이므로 즉시 최종 메시지로 추가
                    addOrUpdateMessage(
                        text = fallbackText,
                        isFinal = true,
                        senderType = SenderType.PARENT,
                        isFeedbackAvailable = true,
                    )

                    // 시스템 TTS로 재생
                    markAppSpeech(fallbackText)
                    ttsPlayer.speak(
                        text = fallbackText,
                        onComplete = {},
                        onError = {},
                    )

                    _lastGlosses.value = emptyList()
                }
        }

        @Suppress("ReturnCount")
        private suspend fun resolveOnDeviceTranslation(
            words: List<String>,
            sentenceType: String?,
        ): String {
            val normalizedWords = normalizeOnDeviceGlosses(words)
            if (normalizedWords.isEmpty()) {
                return ""
            }

            generateDemoSentenceOrNull(normalizedWords)?.let { return it }

            localSignSentenceGenerator
                .generateKnownPatternOrNull(
                    glosses = normalizedWords,
                    sentenceType = sentenceType,
                    speechStyle = onDeviceSpeechStyle,
                )?.let { return it }

            val glossText = normalizedWords.joinToString(" ").trim()

            return runCatching {
                normalizeTranslatedSentence(
                    text = onDeviceTranslationEngine.translate(glossText).koreanText,
                    sentenceType = sentenceType,
                )
            }.getOrElse { throwable ->
                Log.w(
                    TAG,
                    "On-device on-device translation failed. Falling back to local generator.",
                    throwable,
                )
                localSignSentenceGenerator
                    .generate(
                        glosses = normalizedWords,
                        sentenceType = sentenceType,
                        speechStyle = onDeviceSpeechStyle,
                    ).ifBlank {
                        glossText
                    }
            }
        }

        private fun normalizeOnDeviceGlosses(words: List<String>): List<String> =
            words
                .map { word -> word.trim() }
                .filter { word ->
                    word.isNotBlank() && !word.equals(NONE_GLOSS, ignoreCase = true)
                }.fold(emptyList()) { result, word ->
                    if (result.lastOrNull() == word) result else result + word
                }

        private fun generateDemoSentenceOrNull(words: List<String>): String? {
            val compact = words.joinToString(" ")
            return exactStableDemoRules[compact]
                ?: stableSetDemoRules[words.toSet()]
                ?: exactHoldDemoRules[compact]
        }

        private fun normalizeTranslatedSentence(
            text: String,
            sentenceType: String?,
        ): String {
            val trimmed = text.trim()
            if (trimmed.isBlank()) {
                return trimmed
            }

            val bare = trimmed.trimEnd('.', '?', '!')
            return if (isQuestionSentenceType(sentenceType)) {
                "$bare?"
            } else if (trimmed.last() in ".?!") {
                trimmed
            } else {
                "$trimmed."
            }
        }

        private fun isQuestionSentenceType(sentenceType: String?): Boolean =
            sentenceType?.contains("의문") == true ||
                sentenceType.equals("question", ignoreCase = true)

        private fun preloadOnDeviceTranslationEngine() {
            viewModelScope.launch {
                runCatching {
                    onDeviceTranslationEngine.load()
                }.onFailure { throwable ->
                    Log.w(TAG, "On-device on-device preload failed.", throwable)
                }
            }
        }

        private fun startRecordingForStt() {
            if (_sessionState.value != SessionState.Active || !sessionRuntimeStarted) return

            _speechInputPhase.value = SpeechInputPhase.Listening

            // 새 세션 시작 시 상태 초기화 (오염 방지)
            isResultsReceived = false
            isStoppedReceived = false

            when {
                shouldUseCloudStt() -> startCloudRecordingLoop()
                shouldUseLocalStt() -> startLocalSttListening()
                else ->
                    showServerOnlyUnavailableMessage(
                        affectsSign = false,
                        affectsSpeech = true,
                    )
            }
        }

        private fun startLocalSttListening() {
            cloudSttJob?.cancel()
            cloudSttJob = null
            androidAudioRecorder.stop()
            _speechInputPhase.value = SpeechInputPhase.Listening
            currentSttSessionId = sttEngine.nextSessionId()
            sttEngine.startListening(currentSttSessionId)
        }

        private fun startCloudRecordingLoop() {
            if (cloudSttJob?.isActive == true) return

            sttEngine.stopListening()
            _speechInputPhase.value = SpeechInputPhase.Listening
            currentSttSessionId = sttEngine.nextSessionId()

            cloudSttJob =
                viewModelScope.launch {
                    while (isActive && canUseCloudStt()) {
                        val fileName = "stt_audio_$currentSttSessionId"
                        _speechInputPhase.value = SpeechInputPhase.Listening
                        val started = androidAudioRecorder.start(fileName)
                        val nextDelayMs =
                            if (!started) {
                                Log.w(TAG, "Cloud STT recorder start failed")
                                CLOUD_STT_RETRY_DELAY_MS
                            } else {
                                Log.d(TAG, "Cloud STT recording started: $fileName")
                                val audioFile = waitForCloudSpeechFile()
                                if (audioFile != null && canUseCloudStt()) {
                                    _speechInputPhase.value = SpeechInputPhase.Analyzing
                                    updateOrAddChildMessage(
                                        text = CLOUD_STT_ANALYZING_MESSAGE,
                                        isFinal = false,
                                    )
                                    performCloudStt(audioFile)
                                } else {
                                    Log.d(TAG, "Cloud STT recording skipped. Waiting for speech.")
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
                TranslationMode.AUTO -> _isOnline.value
                TranslationMode.SERVER -> _isOnline.value
                TranslationMode.ON_DEVICE -> false
            }

        private fun shouldUseLocalStt(): Boolean =
            when (_translationMode.value) {
                TranslationMode.AUTO -> !_isOnline.value
                TranslationMode.SERVER -> false
                TranslationMode.ON_DEVICE -> true
            }

        private fun canUseCloudStt(): Boolean =
            _sessionState.value == SessionState.Active &&
                sessionRuntimeStarted &&
                shouldUseCloudStt()

        private fun canHandleLocalSttEvent(): Boolean =
            _sessionState.value == SessionState.Active &&
                sessionRuntimeStarted &&
                shouldUseLocalStt()

        private fun canStartLocalSttAfterCloudLoop(): Boolean =
            _sessionState.value == SessionState.Active &&
                sessionRuntimeStarted &&
                shouldUseLocalStt()

        private fun startLocalSttFromCloudLoop() {
            cloudSttJob = null
            androidAudioRecorder.stop()
            _speechInputPhase.value = SpeechInputPhase.Listening
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
                kotlinx.coroutines.delay(getSttRestartDelay())
                startRecordingForStt()
            }
        }

        private fun getSttRestartDelay(): Long =
            if (_speechInputPhase.value == SpeechInputPhase.Unrecognized) {
                STT_UNRECOGNIZED_NOTICE_MS
            } else {
                STT_RESTART_DELAY_MS
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
            if (_sessionState.value == SessionState.Active) return

            _sessionState.value = SessionState.Active
            _signInputPhase.value = SignInputPhase.Preparing
            _speechInputPhase.value = SpeechInputPhase.Listening
            clearAppSpeechEchoFilter()
            resetCloudSttFailures()
            sessionRuntimeStarted = false
            commsSessionJob?.cancel()
            commsSessionId = null
            commsSessionJob =
                viewModelScope.launch {
                    prepareCommsSession()
                    startSessionRuntimeIfActive()
                }
        }

        private suspend fun prepareCommsSession() {
            if (canCreateCommsSessionNow()) {
                ensureCommsSessionForServerStorage()
            } else {
                Log.d(
                    TAG,
                    "Comms session skipped: mode=${_translationMode.value}, online=${_isOnline.value}",
                )
            }
        }

        private suspend fun ensureCommsSessionForServerStorage(): Long? =
            commsSessionMutex.withLock {
                commsSessionId ?: if (canCreateCommsSessionNow()) {
                    createCommsSessionOrNull()
                } else {
                    null
                }
            }

        private suspend fun createCommsSessionOrNull(): Long? {
            val childId = resolveCommsSessionChildId()
            return if (childId == null || _sessionState.value != SessionState.Active) {
                null
            } else {
                commsSessionRepository
                    .createSession(childProfileId = childId)
                    .fold(
                        onSuccess = { sessionId ->
                            if (_sessionState.value == SessionState.Active) {
                                commsSessionId = sessionId
                                Log.d(TAG, "Comms session created: sessionId=$sessionId")
                                sessionId
                            } else {
                                endCommsSessionById(sessionId)
                                null
                            }
                        },
                        onFailure = { throwable ->
                            Log.w(
                                TAG,
                                "Comms session creation failed. Continue without saving.",
                                throwable,
                            )
                            null
                        },
                    )
            }
        }

        private fun canCreateCommsSessionNow(): Boolean =
            _sessionState.value == SessionState.Active && shouldCreateCommsSession()

        private fun shouldCreateCommsSession(): Boolean =
            when (_translationMode.value) {
                TranslationMode.AUTO -> _isOnline.value
                TranslationMode.SERVER -> _isOnline.value
                TranslationMode.ON_DEVICE -> false
            }

        private fun startSessionRuntimeIfActive() {
            if (_sessionState.value != SessionState.Active || sessionRuntimeStarted) return

            sessionRuntimeStarted = true
            signRecognitionEngine.start()

            sttJob?.cancel()
            sttJob =
                viewModelScope.launch {
                    sttEngine.events.collect { event ->
                        if (event.sessionId != currentSttSessionId || !canHandleLocalSttEvent()) {
                            return@collect
                        }

                        handleSttEvent(event)
                    }
                }
            startRecordingForStt()
        }

        private suspend fun handleSttEvent(event: SttEvent) {
            when (event) {
                is SttEvent.Error -> handleLocalSttError(event)
                is SttEvent.PartialResults -> handleSttPartialResults(event.text)
                is SttEvent.Results -> handleSttResults(event.text)
                is SttEvent.EndOfSpeech -> {
                    _speechInputPhase.value = SpeechInputPhase.Analyzing
                }
                is SttEvent.Stopped -> handleSttStopped()
                is SttEvent.VolumeChanged -> _micVolume.value = event.db
                else -> Unit
            }
        }

        private fun handleSttPartialResults(text: String) {
            if (shouldIgnoreOwnSpeech(text)) return

            isResultsReceived = false
            isStoppedReceived = false
            _speechInputPhase.value = SpeechInputPhase.Listening
            updateOrAddChildMessage(
                text,
                isFinal = false,
            )
        }

        private suspend fun handleSttResults(text: String) {
            if (shouldIgnoreOwnSpeech(text)) {
                isResultsReceived = true
                checkAndRestartStt()
                return
            }

            _speechInputPhase.value = SpeechInputPhase.Listening
            updateOrAddChildMessage(
                text,
                isFinal = true,
            )
            isResultsReceived = true
            checkAndRestartStt()
        }

        private suspend fun handleSttStopped() {
            if (_sessionState.value != SessionState.Active) return

            isStoppedReceived = true
            if (shouldUseLocalStt()) {
                isResultsReceived = true // 오프라인은 결과 대기 불필요
            }
            checkAndRestartStt()
        }

        private suspend fun resolveCommsSessionChildId(): Long? =
            when (val state = activeChildProfileManager.getActiveChildProfile()) {
                is ActiveChildProfileState.Selected -> state.profile.childId
                ActiveChildProfileState.Loading -> null
                ActiveChildProfileState.Missing -> {
                    Log.d(TAG, "Comms session skipped: active child is missing")
                    null
                }
                ActiveChildProfileState.NotFound -> {
                    Log.w(TAG, "Comms session skipped: active child not found")
                    null
                }
                is ActiveChildProfileState.Error -> {
                    Log.w(TAG, "Comms session skipped: ${state.message}")
                    null
                }
            }

        private fun endCommsSession() {
            commsSessionJob?.cancel()
            commsSessionJob = null

            val sessionId = commsSessionId ?: return
            commsSessionId = null
            endCommsSessionById(sessionId)
        }

        private fun endCommsSessionById(sessionId: Long) {
            viewModelScope.launch {
                commsSessionRepository
                    .endSession(sessionId)
                    .onSuccess {
                        Log.d(TAG, "Comms session ended: sessionId=$sessionId")
                    }.onFailure { throwable ->
                        Log.w(
                            TAG,
                            "Comms session end failed: sessionId=$sessionId",
                            throwable,
                        )
                    }
            }
        }

        private fun handleLocalSttError(event: SttEvent.Error) {
            Log.w(TAG, "Local STT error: ${event.message}")
            isResultsReceived = true
            isStoppedReceived = false
            if (event.shouldKeepListening()) {
                if (completePendingChildMessage()) {
                    _speechInputPhase.value = SpeechInputPhase.Listening
                } else {
                    _speechInputPhase.value = SpeechInputPhase.Unrecognized
                }
            } else {
                _speechInputPhase.value = SpeechInputPhase.Error
                addSystemMessage("음성 인식에 실패했습니다. 다시 말해 주세요.")
            }
        }

        private suspend fun performCloudStt(audioFile: File): Long {
            var restartDelayMs = STT_RESTART_DELAY_MS
            try {
                Log.d(
                    TAG,
                    "Cloud STT upload started: path=${audioFile.absolutePath}, " +
                        "size=${audioFile.length()}, mime=$CLOUD_STT_AUDIO_MIME_TYPE",
                )
                val sessionId = ensureCommsSessionForServerStorage()
                val result =
                    translateRepository.translateSpeechToText(
                        audioFile = audioFile,
                        mimeType = CLOUD_STT_AUDIO_MIME_TYPE,
                        sessionId = sessionId,
                    )

                result.fold(
                    onSuccess = { response ->
                        handleCloudSttSuccess(response)?.let { delayMs ->
                            restartDelayMs = delayMs
                        }
                    },
                    onFailure = { throwable ->
                        restartDelayMs = handleCloudSttFailure(throwable)
                    },
                )
            } finally {
                deleteCloudSttAudioFile(audioFile)
            }
            return restartDelayMs
        }

        @Suppress("ReturnCount")
        private fun handleCloudSttSuccess(response: SpeechToTextResponse): Long? {
            cloudSttFailureCount = 0
            _speechInputPhase.value = SpeechInputPhase.Listening
            val displayText =
                buildCloudSttDisplayText(
                    recognizedText = response.recognizedText,
                    correctedText = response.correctedText,
                )
            if (
                shouldIgnoreOwnSpeech(response.recognizedText) ||
                shouldIgnoreOwnSpeech(response.correctedText)
            ) {
                removePendingChildMessage()
                Log.d(TAG, "Cloud STT result ignored as app speech echo")
                return null
            }
            if (displayText.isBlank()) {
                removePendingChildMessage()
                _speechInputPhase.value = SpeechInputPhase.Unrecognized
                Log.d(TAG, "Cloud STT result ignored because display text is blank")
                return STT_UNRECOGNIZED_NOTICE_MS
            }
            if (canUseCloudStt()) {
                updateOrAddChildMessage(displayText, isFinal = true)
                _sttText.value = ""
            }
            Log.d(
                TAG,
                "Cloud STT upload succeeded: recognized=${response.recognizedText}, " +
                    "corrected=${response.correctedText}, isCorrected=${response.corrected}",
            )
            return null
        }

        private fun handleCloudSttFailure(throwable: Throwable): Long {
            cloudSttFailureCount++
            val restartDelayMs = getCloudSttFailureRetryDelay()
            removePendingChildMessage()
            _speechInputPhase.value = SpeechInputPhase.Unrecognized
            Log.w(
                TAG,
                "Cloud STT upload failed: failureCount=$cloudSttFailureCount, " +
                    "retryDelayMs=$restartDelayMs",
                throwable,
            )
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
        ): String {
            val normalizedRecognized = recognizedText.trim()
            val normalizedCorrected = correctedText.trim()
            return normalizedRecognized.ifBlank { normalizedCorrected }
        }

        private fun SttEvent.Error.shouldKeepListening(): Boolean =
            type == SttErrorType.SpeechTimeout || type == SttErrorType.NoMatch

        private fun markAppSpeech(text: String) {
            lastAppSpeechText = text
            lastAppSpeechStartedAt = System.currentTimeMillis()
        }

        private fun clearAppSpeechEchoFilter() {
            lastAppSpeechText = ""
            lastAppSpeechStartedAt = 0L
        }

        private fun shouldIgnoreOwnSpeech(text: String): Boolean {
            val appSpeech = normalizeSpeechText(lastAppSpeechText)
            val inputSpeech = normalizeSpeechText(text)
            val isWithinEchoWindow =
                System.currentTimeMillis() - lastAppSpeechStartedAt <= APP_SPEECH_ECHO_FILTER_MS
            val isSameSpeech =
                appSpeech == inputSpeech ||
                    appSpeech.contains(inputSpeech) ||
                    inputSpeech.contains(appSpeech)

            return appSpeech.isNotBlank() &&
                inputSpeech.isNotBlank() &&
                isWithinEchoWindow &&
                isSameSpeech
        }

        private fun normalizeSpeechText(text: String): String =
            text
                .filter { it.isLetterOrDigit() }
                .lowercase()

        private fun handleTtsSuccess(base64Audio: String) {
            markAppSpeech(_translatedText.value)
            audioPlayer.playBase64(
                base64Data = base64Audio,
                onComplete = {},
                onError = {},
            )
        }

        fun updateTranslationMode(mode: TranslationMode) {
            viewModelScope.launch {
                translationModeRepository.saveTranslationMode(mode)
            }
        }

        fun submitTranslationFeedback(
            message: ChatMessage,
            reason: TranslationFeedbackReason,
        ) {
            pendingFeedbackRequest =
                TranslationFeedbackRequest(
                    message = message,
                    reason = reason,
                )
            submitPendingTranslationFeedback()
        }

        fun clearTranslationFeedbackSubmitState() {
            _translationFeedbackSubmitState.value = TranslationFeedbackSubmitState.Idle
            pendingFeedbackRequest = null
        }

        fun stopSession() {
            _sessionState.value = SessionState.Idle
            sessionRuntimeStarted = false
            _signInputPhase.value = SignInputPhase.Idle
            _speechInputPhase.value = SpeechInputPhase.Idle
            endCommsSession()
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
            clearAppSpeechEchoFilter()
            resetCloudSttFailures()
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
            isFeedbackAvailable: Boolean = false,
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
                            isFeedbackAvailable = isFeedbackAvailable,
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
                            isFeedbackAvailable = isFeedbackAvailable,
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

        private fun removePendingChildMessage() {
            _sttText.value = ""
            _messages.update { currentList ->
                val lastMessage = currentList.lastOrNull()
                if (lastMessage?.isPendingChildMessage() == true) {
                    currentList.dropLast(1)
                } else {
                    currentList
                }
            }
        }

        private fun completePendingChildMessage(): Boolean {
            var completed = false
            _sttText.value = ""
            _messages.update { currentList ->
                val lastMessage = currentList.lastOrNull()
                if (lastMessage?.isCompletablePendingChildMessage() == true) {
                    completed = true
                    currentList.dropLast(1) + lastMessage.copy(status = MessageStatus.COMPLETED)
                } else {
                    currentList
                }
            }
            return completed
        }

        private fun ChatMessage.isPendingChildMessage(): Boolean =
            senderType == SenderType.CHILD && status == MessageStatus.PENDING

        private fun ChatMessage.isCompletablePendingChildMessage(): Boolean =
            isPendingChildMessage() && text.isNotBlank()

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

        private fun resetCloudSttFailures() {
            cloudSttFailureCount = 0
        }

        private fun showServerOnlyUnavailableMessage(
            affectsSign: Boolean = true,
            affectsSpeech: Boolean = true,
        ) {
            if (affectsSign) {
                _signInputPhase.value = SignInputPhase.Error
            }
            if (affectsSpeech) {
                _speechInputPhase.value = SpeechInputPhase.Error
            }
            _translationModeNotice.value = NOTICE_SERVER_OFFLINE
            addOrUpdateMessage(
                text = "서버 모드에서는 네트워크 연결이 필요합니다.",
                isFinal = true,
                senderType = SenderType.PARENT,
            )
            _lastGlosses.value = emptyList()
        }

        private fun submitPendingTranslationFeedback() {
            val request = pendingFeedbackRequest ?: return
            if (
                _translationFeedbackSubmitState.value ==
                TranslationFeedbackSubmitState.Submitting
            ) {
                return
            }

            _translationFeedbackSubmitState.value =
                TranslationFeedbackSubmitState.Submitting
            viewModelScope.launch {
                translateRepository
                    .submitTranslationFeedback(
                        message = request.message,
                        reason = request.reason,
                    ).onSuccess {
                        _translationFeedbackSubmitState.value =
                            TranslationFeedbackSubmitState.Success
                    }.onFailure {
                        _translationFeedbackSubmitState.value =
                            TranslationFeedbackSubmitState.Error(
                                message = "피드백 제출에 실패했습니다. 다시 시도해 주세요.",
                            )
                    }
            }
        }

        override fun onCleared() {
            endCommsSession()
            signRecognitionEngine.stop()
            audioPlayer.stop()
            ttsPlayer.stop()
            stopRecordingForStt()
            translationJob?.cancel()
            completionTimerJob?.cancel()
            sttJob?.cancel()
            cloudSttJob?.cancel()
            commsSessionJob?.cancel()
            super.onCleared()
        }

        fun onLandmarkFrame(frame: LandmarkFrameResult) {
            if (_sessionState.value != SessionState.Active) return

            signRecognitionEngine.submitFrame(frame)
        }

        private data class TranslationFeedbackRequest(
            val message: ChatMessage,
            val reason: TranslationFeedbackReason,
        )

        companion object {
            private const val TAG = "ConversationViewModel"
            private const val NONE_GLOSS = "none"
            private const val COMPLETION_THRESHOLD_MS = 2000L
            private const val STT_RESTART_DELAY_MS = 500L
            private const val STT_UNRECOGNIZED_NOTICE_MS = 1200L
            private const val CLOUD_STT_POLL_INTERVAL_MS = 150L
            private const val CLOUD_STT_MIN_RECORDING_MS = 500L
            private const val CLOUD_STT_MIN_UPLOAD_RECORDING_MS = 800L
            private const val CLOUD_STT_SILENCE_TIMEOUT_MS = 900L
            private const val CLOUD_STT_MAX_RECORDING_MS = 6000L
            private const val CLOUD_STT_NO_SPEECH_TIMEOUT_MS = 1800L
            private const val CLOUD_STT_RETRY_DELAY_MS = 500L
            private const val CLOUD_STT_FAILURE_RETRY_DELAY_MS = 1500L
            private const val CLOUD_STT_MAX_FAILURE_RETRY_DELAY_MS = 5000L
            private const val CLOUD_STT_VOICE_THRESHOLD = 2000
            private const val CLOUD_STT_FALLBACK_VOICE_THRESHOLD = 1500
            private const val CLOUD_STT_MIN_VOICE_FRAME_COUNT = 2
            private const val CLOUD_STT_MIN_FILE_BYTES = 1024L
            private const val CLOUD_STT_STOP_REASON_SILENCE = "silence"
            private const val CLOUD_STT_STOP_REASON_MAX_DURATION = "max_duration"
            private const val CLOUD_STT_STOP_REASON_NO_SPEECH = "no_speech"
            private const val CLOUD_STT_STOP_REASON_CANCELLED = "cancelled"
            private const val CLOUD_STT_ANALYZING_MESSAGE = "대화 내용을 분석 중입니다..."
            private const val CLOUD_STT_AUDIO_MIME_TYPE = "audio/wav"
            private const val APP_SPEECH_ECHO_FILTER_MS = 5000L
            private val exactStableDemoRules =
                mapOf(
                    "엄마 아프다 치료" to "엄마가 아파서 치료가 필요해요.",
                    "시험 모르다 위로" to "시험을 몰라서 위로가 필요해요.",
                    "엄마 서점 독서" to "엄마가 서점에서 독서해요.",
                    "서점 독서 좋다" to "서점에서 독서하는 게 좋아요.",
                )
            private val stableSetDemoRules =
                mapOf(
                    setOf("엄마", "아프다", "치료") to "엄마가 아파서 치료가 필요해요.",
                    setOf("시험", "모르다", "위로") to "시험을 몰라서 위로가 필요해요.",
                    setOf("엄마", "서점", "독서") to "엄마가 서점에서 독서해요.",
                )
            private val exactHoldDemoRules =
                mapOf(
                    "엄마 아프다 의사" to "엄마가 아파서 의사를 만나야 해요.",
                    "의사 치료 위로" to "의사가 치료하고 위로해 줘요.",
                    "서점 가다 독서" to "서점에 가서 독서해요.",
                    "엄마 병원 가다" to "엄마가 병원에 가요.",
                    "병원 치료 받다" to "병원에서 치료를 받아요.",
                    "우유 주다 감사" to "우유를 줘서 감사해요.",
                )
            private const val NOTICE_AUTO_OFFLINE_FALLBACK =
                "자동 모드: 네트워크가 없어 기기 내 처리로 전환했어요."
            private const val NOTICE_AUTO_SERVER_FALLBACK =
                "자동 모드: 서버 처리 실패로 기기 내 처리로 전환했어요."
            private const val NOTICE_SERVER_OFFLINE =
                "서버 모드: 네트워크 연결 후 다시 사용할 수 있어요."
        }
    }
