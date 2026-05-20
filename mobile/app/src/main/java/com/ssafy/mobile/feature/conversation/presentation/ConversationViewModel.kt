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
import com.ssafy.mobile.feature.conversation.data.remote.model.TranslationSttModeDto
import com.ssafy.mobile.feature.conversation.data.repository.CommsSessionRepository
import com.ssafy.mobile.feature.conversation.data.repository.TranslationApiException
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.LocalSignSentenceGenerator
import com.ssafy.mobile.feature.conversation.domain.model.MessageStatus
import com.ssafy.mobile.feature.conversation.domain.model.SenderType
import com.ssafy.mobile.feature.conversation.domain.model.TranslationFeedbackReason
import com.ssafy.mobile.feature.conversation.domain.model.TranslationMode
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import com.ssafy.mobile.feature.conversation.domain.repository.TranslationModeRepository
import com.ssafy.mobile.feature.conversation.domain.streaming.TranslationStreamingSttClient
import com.ssafy.mobile.feature.conversation.domain.streaming.TranslationStreamingSttConnection
import com.ssafy.mobile.feature.conversation.domain.streaming.TranslationStreamingSttEvent
import com.ssafy.mobile.translation.OnDeviceTranslationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeout

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

@Suppress("LargeClass", "LongParameterList", "TooManyFunctions", "UnusedPrivateMember")
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
        private val localSignSentenceGenerator: LocalSignSentenceGenerator,
        private val onDeviceTranslationEngine: OnDeviceTranslationEngine,
        private val activeChildProfileManager: ActiveChildProfileManager,
        private val commsSessionRepository: CommsSessionRepository,
        private val streamingSttClient: TranslationStreamingSttClient,
        private val translationModeRepository: TranslationModeRepository,
    ) : ViewModel() {
        private val _sessionState = MutableStateFlow(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private val _signInputPhase = MutableStateFlow(SignInputPhase.Idle)
        val signInputPhase: StateFlow<SignInputPhase> = _signInputPhase.asStateFlow()

        private val _speechInputPhase = MutableStateFlow(SpeechInputPhase.Idle)
        val speechInputPhase: StateFlow<SpeechInputPhase> = _speechInputPhase.asStateFlow()

        private val _isOnline = MutableStateFlow(true)
        val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

        private val _translationMode = MutableStateFlow(TranslationMode.ON_DEVICE)
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
        private var serverSttMode = TranslationSttModeDto.REST
        private var streamingFallbackToRest = false
        private var cloudStreamingConnection: TranslationStreamingSttConnection? = null
        private var pendingFeedbackRequest: TranslationFeedbackRequest? = null
        private val onDeviceSpeechStyle = LocalSignSentenceGenerator.SpeechStyle.Polite

        init {
            viewModelScope.launch {
                translationModeRepository.translationMode.collect { mode ->
                    val previousMode = _translationMode.value
                    _translationMode.value = mode
                    if (
                        previousMode != mode &&
                        _sessionState.value == SessionState.Active &&
                        sessionRuntimeStarted
                    ) {
                        stopRecordingForStt()
                        prepareCommsSession()
                        startRecordingForStt()
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
        }

        private fun handleUtterance(event: SignRecognitionEvent.Utterance) {
            if (event.glosses.isEmpty()) return

            _lastGlosses.value = event.glosses
            _signInputPhase.value = SignInputPhase.Collecting
            completionTimerJob?.cancel()
            requestTranslation(
                words = event.glosses,
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

        private fun requestTranslation(
            words: List<String>,
            sentenceType: String? = null,
        ) {
            translationJob?.cancel()
            _signInputPhase.value = SignInputPhase.Translating
            if (applyDemoTranslationIfMatched(words, sentenceType)) {
                return
            }

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

        private fun applyDemoTranslationIfMatched(
            words: List<String>,
            sentenceType: String?,
        ): Boolean {
            val normalizedWords = normalizeOnDeviceGlosses(words)
            val demoSentence =
                generateDemoSentenceOrNull(
                    words = normalizedWords,
                    sentenceType = sentenceType,
                ) ?: return false

            Log.d(
                TAG,
                "Demo translation rule applied before mode routing. " +
                    "mode=${_translationMode.value}, online=${_isOnline.value}, " +
                    "sentenceType=$sentenceType, gloss=${normalizedWords.joinToString(" ")}",
            )
            _translationModeNotice.value = null
            _translatedText.value = demoSentence
            addOrUpdateMessage(
                text = demoSentence,
                isFinal = true,
                senderType = SenderType.PARENT,
                isFeedbackAvailable = true,
            )
            speakTranslatedTextWithDeviceTts(demoSentence)
            _lastGlosses.value = emptyList()
            return true
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
                        runCatching {
                            withTimeout(SIGN_TRANSLATION_TIMEOUT_MS) {
                                translateRepository.translateSignToSpeech(
                                    words = words,
                                    sessionId = sessionId,
                                )
                            }
                        }.getOrElse { throwable ->
                            Result.failure(throwable)
                        }

                    result.fold(
                        onSuccess = { response ->
                            val correctedText = adaptCaregiverLabelForDemo(response.correctedText)
                            _translationModeNotice.value = null
                            _translatedText.value = correctedText
                            addOrUpdateMessage(
                                text = correctedText,
                                isFinal = true,
                                senderType = SenderType.PARENT,
                                isFeedbackAvailable = true,
                            )

                            val audioBase64 = response.audioBase64
                            if (
                                audioBase64.isNullOrBlank() ||
                                correctedText != response.correctedText
                            ) {
                                speakTranslatedTextWithDeviceTts(correctedText)
                            } else {
                                handleTtsSuccess(audioBase64)
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
                    val translatedText = adaptCaregiverLabelForDemo(fallbackText)
                    _translatedText.value = translatedText

                    // 오프라인이므로 즉시 최종 메시지로 추가
                    addOrUpdateMessage(
                        text = translatedText,
                        isFinal = true,
                        senderType = SenderType.PARENT,
                        isFeedbackAvailable = true,
                    )

                    // 시스템 TTS로 재생
                    markAppSpeech(translatedText)
                    ttsPlayer.speak(
                        text = translatedText,
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

            generateDemoSentenceOrNull(normalizedWords, sentenceType)?.let { return it }

            localSignSentenceGenerator
                .generateKnownPatternOrNull(
                    glosses = normalizedWords,
                    sentenceType = sentenceType,
                    speechStyle = onDeviceSpeechStyle,
                )?.let { return it }

            val glossText = normalizedWords.joinToString(" ").trim()

            return runCatching {
                val translatedText =
                    onDeviceTranslationEngine
                        .translate(
                            glossText = glossText,
                            sentenceType = sentenceType,
                        ).koreanText

                normalizeTranslatedSentence(
                    text = translatedText,
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

        private fun generateDemoSentenceOrNull(
            words: List<String>,
            sentenceType: String?,
        ): String? {
            val compact = words.joinToString(" ")
            val rule =
                exactStableDemoRules[compact]
                    ?: stableSetDemoRules[words.toSet()]
                    ?: generateContextualDemoRuleOrNull(words)
                    ?: exactHoldDemoRules[compact]
            return rule?.resolve(isQuestionSentenceType(sentenceType))
        }

        private fun generateContextualDemoRuleOrNull(words: List<String>): DemoSentenceRule? {
            val wordSet = words.toSet()
            return when {
                wordSet.containsAll(setOf("비", "조심", "아프다")) -> carefulRainWalkRule
                else -> null
            }
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

        private fun adaptCaregiverLabelForDemo(text: String): String = text.replace("엄마", "아빠")

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

            if (shouldUseCloudStt()) {
                startCloudRecordingLoop()
            } else {
                sttEngine.stopListening()
                androidAudioRecorder.stop()
                _speechInputPhase.value = SpeechInputPhase.Error
                _translationModeNotice.value = NOTICE_SERVER_OFFLINE
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
            if (shouldUseStreamingStt()) {
                startCloudStreamingLoop()
            } else {
                startCloudRestRecordingLoop()
            }
        }

        private fun startCloudRestRecordingLoop() {
            if (cloudSttJob?.isActive == true) return

            sttEngine.stopListening()
            cloudStreamingConnection?.close()
            cloudStreamingConnection = null
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

        private fun startCloudStreamingLoop() {
            if (cloudSttJob?.isActive == true) return

            sttEngine.stopListening()
            androidAudioRecorder.stop()
            _speechInputPhase.value = SpeechInputPhase.Listening
            currentSttSessionId = sttEngine.nextSessionId()

            cloudSttJob =
                viewModelScope.launch {
                    while (isActive && canUseCloudStt() && shouldUseStreamingStt()) {
                        val restartDelayMs = performCloudStreamingStt()
                        if (canUseCloudStt() && shouldUseStreamingStt()) {
                            delay(restartDelayMs)
                        }
                    }

                    if (canUseCloudStt() && !shouldUseStreamingStt()) {
                        cloudSttJob = null
                        startCloudRecordingLoop()
                    } else if (canStartLocalSttAfterCloudLoop()) {
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
                        maxConsecutiveVoiceFrameCount = maxConsecutiveVoiceFrameCount,
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

        private fun shouldUseCloudStt(): Boolean = _isOnline.value

        private fun shouldUseStreamingStt(): Boolean =
            when (serverSttMode) {
                TranslationSttModeDto.REST -> false
                TranslationSttModeDto.STREAMING -> true
                TranslationSttModeDto.AUTO -> !streamingFallbackToRest
            }

        private fun canUseCloudStt(): Boolean =
            _sessionState.value == SessionState.Active &&
                sessionRuntimeStarted &&
                shouldUseCloudStt()

        private fun canHandleLocalSttEvent(): Boolean =
            _sessionState.value == SessionState.Active &&
                sessionRuntimeStarted &&
                LOCAL_STT_ENABLED

        private fun canStartLocalSttAfterCloudLoop(): Boolean =
            _sessionState.value == SessionState.Active &&
                sessionRuntimeStarted &&
                LOCAL_STT_ENABLED

        private suspend fun refreshServerSttMode() {
            translateRepository
                .getSttMode()
                .onSuccess { mode ->
                    serverSttMode = mode
                    if (mode != TranslationSttModeDto.AUTO) {
                        streamingFallbackToRest = false
                    }
                    Log.d(TAG, "Translation STT mode loaded: $mode")
                }.onFailure { throwable ->
                    serverSttMode = TranslationSttModeDto.REST
                    streamingFallbackToRest = false
                    Log.w(TAG, "Translation STT mode load failed. fallback to REST.", throwable)
                }
        }

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
            maxConsecutiveVoiceFrameCount: Int,
        ): Boolean =
            isValidCloudAudioFile(file) &&
                recordingDuration >= CLOUD_STT_MIN_UPLOAD_RECORDING_MS &&
                voiceFrameCount >= CLOUD_STT_MIN_VOICE_FRAME_COUNT &&
                maxConsecutiveVoiceFrameCount >= CLOUD_STT_MIN_CONSECUTIVE_VOICE_FRAME_COUNT &&
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
            cloudStreamingConnection?.close()
            cloudStreamingConnection = null
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
            if (shouldUseCloudStt()) {
                refreshServerSttMode()
            }

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

        private fun shouldCreateCommsSession(): Boolean = _isOnline.value

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
            if (LOCAL_STT_ENABLED) {
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
                val sessionResult =
                    translateRepository.translateSpeechToText(
                        audioFile = audioFile,
                        mimeType = CLOUD_STT_AUDIO_MIME_TYPE,
                        sessionId = sessionId,
                    )
                val result =
                    recoverCloudSttSessionFailure(
                        result = sessionResult,
                        audioFile = audioFile,
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
        private suspend fun performCloudStreamingStt(): Long {
            val sessionId = ensureCommsSessionForServerStorage()
            if (sessionId == null) {
                Log.w(TAG, "Streaming STT skipped because comms session is not available.")
                return handleCloudStreamingFailure(
                    IllegalStateException("Comms session is not available."),
                )
            }

            val resultDelay = CompletableDeferred<Long>()
            val startDelay = CompletableDeferred<Long?>()
            val finalText = StringBuilder()
            val connection =
                streamingSttClient.start(
                    sessionId = sessionId,
                    listener =
                        object : TranslationStreamingSttClient.Listener {
                            override fun onEvent(event: TranslationStreamingSttEvent) {
                                viewModelScope.launch {
                                    handleCloudStreamingSttEvent(
                                        event = event,
                                        finalText = finalText,
                                        startDelay = startDelay,
                                        resultDelay = resultDelay,
                                    )
                                }
                            }
                        },
                )
            cloudStreamingConnection = connection

            val startFailureDelay =
                runCatching {
                    withTimeout(CLOUD_STREAMING_START_TIMEOUT_MS) {
                        startDelay.await()
                    }
                }.getOrElse { throwable ->
                    connection.close()
                    cloudStreamingConnection = null
                    return handleCloudStreamingFailure(throwable)
                }

            if (startFailureDelay != null) {
                return startFailureDelay
            }

            val started =
                androidAudioRecorder.startPcmStream { audioBytes ->
                    if (!connection.sendAudio(audioBytes)) {
                        Log.w(TAG, "Streaming STT audio chunk send failed.")
                    }
                }

            if (!started) {
                connection.close()
                cloudStreamingConnection = null
                return handleCloudStreamingFailure(
                    IllegalStateException("Streaming STT recorder start failed."),
                )
            }

            val shouldRequestResult = waitForCloudStreamingSpeechEnd()
            androidAudioRecorder.stop()

            if (!shouldRequestResult) {
                connection.close()
                cloudStreamingConnection = null
                return CLOUD_STT_RETRY_DELAY_MS
            }

            _speechInputPhase.value = SpeechInputPhase.Analyzing
            updateOrAddChildMessage(
                text = CLOUD_STT_ANALYZING_MESSAGE,
                isFinal = false,
            )
            connection.end()

            return runCatching {
                withTimeout(CLOUD_STREAMING_RESULT_TIMEOUT_MS) {
                    resultDelay.await()
                }
            }.getOrElse { throwable ->
                connection.close()
                cloudStreamingConnection = null
                handleCloudStreamingFailure(throwable)
            }
        }

        private suspend fun waitForCloudStreamingSpeechEnd(): Boolean {
            val startedAt = System.currentTimeMillis()
            var speechDetected = false
            var lastVoiceAt = startedAt
            var peakAmplitude = 0
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

                val stopReason =
                    getCloudRecordingStopReason(
                        speechDetected = speechDetected,
                        recordingDuration = recordingDuration,
                        silenceDuration = now - lastVoiceAt,
                    )
                if (stopReason != null) {
                    Log.d(
                        TAG,
                        "Streaming STT recording stopped: speechDetected=$speechDetected, " +
                            "peakAmplitude=$peakAmplitude, duration=$recordingDuration, " +
                            "reason=$stopReason, voiceFrames=$voiceFrameCount",
                    )
                    return recordingDuration >= CLOUD_STT_MIN_UPLOAD_RECORDING_MS &&
                        voiceFrameCount >= CLOUD_STT_MIN_VOICE_FRAME_COUNT &&
                        maxConsecutiveVoiceFrameCount >=
                        CLOUD_STT_MIN_CONSECUTIVE_VOICE_FRAME_COUNT &&
                        (
                            speechDetected ||
                                peakAmplitude >= CLOUD_STT_FALLBACK_VOICE_THRESHOLD
                        )
                }
            }

            return false
        }

        @Suppress("CyclomaticComplexMethod")
        private fun handleCloudStreamingSttEvent(
            event: TranslationStreamingSttEvent,
            finalText: StringBuilder,
            startDelay: CompletableDeferred<Long?>,
            resultDelay: CompletableDeferred<Long>,
        ) {
            when (event) {
                TranslationStreamingSttEvent.Started,
                TranslationStreamingSttEvent.Configured,
                -> {
                    if (!startDelay.isCompleted) {
                        startDelay.complete(null)
                    }
                }
                is TranslationStreamingSttEvent.Partial -> {
                    val displayText =
                        buildStreamingDisplayText(
                            finalText = finalText.toString(),
                            currentText = event.recognizedText.ifBlank { event.correctedText },
                        )
                    if (displayText.isNotBlank() && !shouldIgnoreOwnSpeech(displayText)) {
                        updateOrAddChildMessage(displayText, isFinal = false)
                    }
                }
                is TranslationStreamingSttEvent.Final -> {
                    appendStreamingFinalText(
                        finalText = finalText,
                        text = event.recognizedText.ifBlank { event.correctedText },
                    )
                    val displayText = finalText.toString().trim()
                    if (displayText.isNotBlank() && !shouldIgnoreOwnSpeech(displayText)) {
                        updateOrAddChildMessage(displayText, isFinal = false)
                    }
                }
                is TranslationStreamingSttEvent.Error -> {
                    if (!resultDelay.isCompleted) {
                        val cause = event.cause ?: RuntimeException(event.message)
                        val restartDelay = handleCloudStreamingFailure(cause)
                        if (!startDelay.isCompleted) {
                            startDelay.complete(restartDelay)
                        }
                        resultDelay.complete(restartDelay)
                    }
                }
                TranslationStreamingSttEvent.Closed -> {
                    cloudStreamingConnection?.close()
                    cloudStreamingConnection = null
                    if (!resultDelay.isCompleted) {
                        val restartDelay = handleCloudStreamingClosed(finalText.toString())
                        if (!startDelay.isCompleted) {
                            startDelay.complete(restartDelay)
                        }
                        resultDelay.complete(restartDelay)
                    }
                }
            }
        }

        private fun appendStreamingFinalText(
            finalText: StringBuilder,
            text: String,
        ) {
            val normalizedText = text.trim()
            if (normalizedText.isBlank()) return

            if (
                finalText.isNotEmpty() &&
                !finalText.last().isWhitespace() &&
                !normalizedText.first().isWhitespace()
            ) {
                finalText.append(' ')
            }
            finalText.append(normalizedText)
        }

        private fun buildStreamingDisplayText(
            finalText: String,
            currentText: String,
        ): String =
            listOf(finalText.trim(), currentText.trim())
                .filter { it.isNotBlank() }
                .joinToString(" ")

        private fun handleCloudStreamingClosed(finalText: String): Long {
            cloudSttFailureCount = 0
            _speechInputPhase.value = SpeechInputPhase.Listening
            val displayText = finalText.trim()
            return when {
                displayText.isBlank() -> {
                    removePendingChildMessage()
                    _speechInputPhase.value = SpeechInputPhase.Unrecognized
                    STT_UNRECOGNIZED_NOTICE_MS
                }
                shouldIgnoreOwnSpeech(displayText) -> {
                    removePendingChildMessage()
                    STT_RESTART_DELAY_MS
                }
                else -> {
                    updateOrAddChildMessage(displayText, isFinal = true)
                    STT_RESTART_DELAY_MS
                }
            }
        }

        private fun handleCloudStreamingFailure(throwable: Throwable): Long {
            if (serverSttMode == TranslationSttModeDto.AUTO) {
                streamingFallbackToRest = true
                Log.w(TAG, "Streaming STT failed. fallback to REST STT.", throwable)
            } else {
                Log.w(TAG, "Streaming STT failed.", throwable)
            }
            cloudStreamingConnection?.close()
            cloudStreamingConnection = null
            return handleCloudSttFailure(throwable)
        }

        private suspend fun recoverCloudSttSessionFailure(
            result: Result<SpeechToTextResponse>,
            audioFile: File,
            sessionId: Long?,
        ): Result<SpeechToTextResponse> {
            val throwable = result.exceptionOrNull()
            if (sessionId == null || throwable?.isCommsSessionNotFound() != true) {
                return result
            }

            invalidateCommsSessionIfMatches(sessionId)
            Log.w(
                TAG,
                "Cloud STT session was not found. Retrying without sessionId: sessionId=$sessionId",
                throwable,
            )
            return translateRepository.translateSpeechToText(
                audioFile = audioFile,
                mimeType = CLOUD_STT_AUDIO_MIME_TYPE,
                sessionId = null,
            )
        }

        private suspend fun invalidateCommsSessionIfMatches(sessionId: Long) {
            commsSessionMutex.withLock {
                if (commsSessionId == sessionId) {
                    commsSessionId = null
                }
            }
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
            if (throwable.isUnrecognizableCloudSttFailure()) {
                cloudSttFailureCount = 0
                removePendingChildMessage()
                _speechInputPhase.value = SpeechInputPhase.Unrecognized
                Log.w(TAG, "Cloud STT audio was not recognized.", throwable)
                return STT_UNRECOGNIZED_NOTICE_MS
            }

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

        private fun Throwable.isCommsSessionNotFound(): Boolean =
            (this as? TranslationApiException)
                ?.errorCode == ERROR_CODE_COMMS_SESSION_NOT_FOUND

        private fun Throwable.isUnrecognizableCloudSttFailure(): Boolean {
            val apiException = this as? TranslationApiException ?: return false
            return apiException.errorCode in UNRECOGNIZABLE_STT_ERROR_CODES
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

        private fun speakTranslatedTextWithDeviceTts(text: String) {
            if (text.isBlank()) {
                startRecordingForStt()
                return
            }

            markAppSpeech(text)
            ttsPlayer.speak(
                text = text,
                onComplete = { startRecordingForStt() },
                onError = { startRecordingForStt() },
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
            streamingFallbackToRest = false
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

        private data class DemoSentenceRule(
            val statement: String,
            val question: String,
        ) {
            fun resolve(isQuestion: Boolean): String = if (isQuestion) question else statement
        }

        companion object {
            private const val TAG = "ConversationViewModel"
            private const val SIGN_TRANSLATION_TIMEOUT_MS = 6000L
            private const val NONE_GLOSS = "none"
            private const val STT_RESTART_DELAY_MS = 500L
            private const val STT_UNRECOGNIZED_NOTICE_MS = 1200L
            private const val LOCAL_STT_ENABLED = false
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
            private const val CLOUD_STT_MIN_VOICE_FRAME_COUNT = 4
            private const val CLOUD_STT_MIN_CONSECUTIVE_VOICE_FRAME_COUNT = 3
            private const val CLOUD_STT_MIN_FILE_BYTES = 1024L
            private const val CLOUD_STT_STOP_REASON_SILENCE = "silence"
            private const val CLOUD_STT_STOP_REASON_MAX_DURATION = "max_duration"
            private const val CLOUD_STT_STOP_REASON_NO_SPEECH = "no_speech"
            private const val CLOUD_STT_STOP_REASON_CANCELLED = "cancelled"
            private const val CLOUD_STT_ANALYZING_MESSAGE = "대화 내용을 분석 중입니다..."
            private const val CLOUD_STT_AUDIO_MIME_TYPE = "audio/wav"
            private const val ERROR_CODE_COMMS_SESSION_NOT_FOUND = "COMMUNICATION_SESSION_NOT_FOUND"
            private const val ERROR_CODE_STT_RECOGNITION_FAILED =
                "TRANSLATION_SPEECH_RECOGNITION_FAILED"
            private const val ERROR_CODE_STT_UNRECOGNIZABLE_AUDIO =
                "TRANSLATION_UNRECOGNIZABLE_AUDIO"
            private const val CLOUD_STREAMING_START_TIMEOUT_MS = 5_000L
            private const val CLOUD_STREAMING_RESULT_TIMEOUT_MS = 35_000L
            private const val APP_SPEECH_ECHO_FILTER_MS = 5000L
            private val UNRECOGNIZABLE_STT_ERROR_CODES =
                setOf(
                    ERROR_CODE_STT_RECOGNITION_FAILED,
                    ERROR_CODE_STT_UNRECOGNIZABLE_AUDIO,
                )
            private val goRestaurantRule =
                DemoSentenceRule(
                    statement = "배고프면 아빠랑 식당에 가자.",
                    question = "배고프면 아빠랑 식당에 갈까?",
                )
            private val comfortScaredRule =
                DemoSentenceRule(
                    statement = "무서우면 아빠가 안아줄게.",
                    question = "무서우면 아빠가 안아줄까?",
                )
            private val treatPainRule =
                DemoSentenceRule(
                    statement = "아프면 아빠가 치료해줄게.",
                    question = "아프면 아빠가 치료해줄까?",
                )
            private val carefulHandRule =
                DemoSentenceRule(
                    statement = "손 조심해, 다치면 아파.",
                    question = "손 조심할까? 다치면 아파.",
                )
            private val sleepBlanketRule =
                DemoSentenceRule(
                    statement = "이불 덮고 자자.",
                    question = "이불 덮고 잘까?",
                )
            private val coverBlanketRule =
                DemoSentenceRule(
                    statement = "잘 때 아빠가 이불 덮어줄게.",
                    question = "잘 때 아빠가 이불 덮어줄까?",
                )
            private val helpUnknownRule =
                DemoSentenceRule(
                    statement = "모르면 아빠가 도와줄게.",
                    question = "모르면 아빠가 도와줄까?",
                )
            private val comfortSadRule =
                DemoSentenceRule(
                    statement = "슬프면 아빠가 안아줄게.",
                    question = "슬프면 아빠가 안아줄까?",
                )
            private val carefulWalkRule =
                DemoSentenceRule(
                    statement = "조심해서 걸어, 다치면 아파.",
                    question = "조심해서 걸을까? 다치면 아파.",
                )
            private val carefulRainWalkRule =
                DemoSentenceRule(
                    statement = "비 오니까 조심해, 다치면 아파.",
                    question = "비 오니까 조심할까? 다치면 아파.",
                )
            private val carefulRainRule =
                DemoSentenceRule(
                    statement = "비 오니까 조심하자.",
                    question = "비 오니까 조심할까?",
                )
            private val comfortWindRule =
                DemoSentenceRule(
                    statement = "바람이 무서우면 아빠가 안아줄게.",
                    question = "바람이 무서우면 아빠가 안아줄까?",
                )
            private val comfortMistakeRule =
                DemoSentenceRule(
                    statement = "실수해도 괜찮아, 아빠가 있어.",
                    question = "실수해도 괜찮아, 아빠가 있을까?",
                )
            private val exactStableDemoRules =
                mapOf(
                    "배고프다 식당 엄마 가다" to goRestaurantRule,
                    "배고프다 엄마 식당" to goRestaurantRule,
                    "무섭다 엄마 위로" to comfortScaredRule,
                    "아프다 엄마 치료" to treatPainRule,
                    "손 조심 아프다" to carefulHandRule,
                    "이불 자다" to sleepBlanketRule,
                    "자다 엄마 이불" to coverBlanketRule,
                    "모르다 엄마 돕다" to helpUnknownRule,
                    "슬프다 엄마 위로" to comfortSadRule,
                    "비 조심 걷다 아프다" to carefulRainWalkRule,
                    "비 조심 아프다" to carefulRainWalkRule,
                    "조심 걷다 아프다" to carefulWalkRule,
                    "비 조심" to carefulRainRule,
                    "바람 무섭다 엄마 위로" to comfortWindRule,
                    "실수 괜찮다 엄마 위로" to comfortMistakeRule,
                )
            private val stableSetDemoRules =
                mapOf(
                    setOf("배고프다", "식당", "엄마", "가다") to goRestaurantRule,
                    setOf("배고프다", "엄마", "식당") to goRestaurantRule,
                    setOf("무섭다", "엄마", "위로") to comfortScaredRule,
                    setOf("아프다", "엄마", "치료") to treatPainRule,
                    setOf("손", "조심", "아프다") to carefulHandRule,
                    setOf("이불", "자다") to sleepBlanketRule,
                    setOf("자다", "엄마", "이불") to coverBlanketRule,
                    setOf("모르다", "엄마", "돕다") to helpUnknownRule,
                    setOf("슬프다", "엄마", "위로") to comfortSadRule,
                    setOf("비", "조심", "걷다", "아프다") to carefulRainWalkRule,
                    setOf("비", "조심", "아프다") to carefulRainWalkRule,
                    setOf("조심", "걷다", "아프다") to carefulWalkRule,
                    setOf("비", "조심") to carefulRainRule,
                    setOf("바람", "무섭다", "엄마", "위로") to comfortWindRule,
                    setOf("실수", "괜찮다", "엄마", "위로") to comfortMistakeRule,
                )
            private val exactHoldDemoRules =
                emptyMap<String, DemoSentenceRule>()
            private const val NOTICE_AUTO_OFFLINE_FALLBACK =
                "자동 모드: 네트워크가 없어 기기 내 처리로 전환했어요."
            private const val NOTICE_AUTO_SERVER_FALLBACK =
                "자동 모드: 서버 처리 실패로 기기 내 처리로 전환했어요."
            private const val NOTICE_SERVER_OFFLINE =
                "서버 모드: 네트워크 연결 후 다시 사용할 수 있어요."
        }
    }
