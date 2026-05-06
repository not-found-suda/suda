package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.audio.AndroidAudioRecorder
import com.ssafy.mobile.core.network.NetworkMonitor
import com.ssafy.mobile.core.stt.SttEngine
import com.ssafy.mobile.core.stt.SttErrorType
import com.ssafy.mobile.core.stt.SttEvent
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
@HiltViewModel
class QuizRecordingViewModel
    @Inject
    constructor(
        private val sttEngine: SttEngine,
        private val audioRecorder: AndroidAudioRecorder,
        private val translateRepository: TranslateRepository,
        private val networkMonitor: NetworkMonitor,
    ) : ViewModel() {
        private val _status = MutableStateFlow(QuizRecordingStatus.Idle)
        val status: StateFlow<QuizRecordingStatus> = _status.asStateFlow()

        private val _recognizedText = MutableStateFlow<String?>(null)
        val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

        private var activeSessionId = INITIAL_SESSION_ID
        private var processingFallbackJob: Job? = null
        private var serverUploadJob: Job? = null
        private var isOnline = true
        private var recordingMode = RecordingMode.None
        private var isLocalFallback = false

        init {
            viewModelScope.launch {
                sttEngine.events.collect { event ->
                    if (event.sessionId == activeSessionId) {
                        handleSttEvent(event)
                    }
                }
            }
            viewModelScope.launch {
                networkMonitor.isOnline.collect { online ->
                    isOnline = online
                }
            }
        }

        fun startListening() {
            cancelProcessingFallback()
            cancelServerUpload()
            activeSessionId = sttEngine.nextSessionId()
            _recognizedText.value = null
            if (isOnline && audioRecorder.start(SERVER_STT_FILE_NAME)) {
                recordingMode = RecordingMode.Server
                isLocalFallback = false
                _status.value = QuizRecordingStatus.Recording
            } else {
                startLocalListening(isFallback = false)
            }
        }

        fun stopListening() {
            if (
                _status.value == QuizRecordingStatus.Recording ||
                _status.value == QuizRecordingStatus.FallbackRecording
            ) {
                when (recordingMode) {
                    RecordingMode.Server -> stopServerRecordingAndUpload()
                    RecordingMode.Local -> {
                        moveToProcessing()
                        sttEngine.stopListening()
                    }
                    RecordingMode.None -> Unit
                }
            }
        }

        fun reset() {
            activeSessionId = sttEngine.nextSessionId()
            cancelProcessingFallback()
            cancelServerUpload()
            sttEngine.stopListening()
            audioRecorder.stop()?.delete()
            recordingMode = RecordingMode.None
            isLocalFallback = false
            _status.value = QuizRecordingStatus.Idle
            _recognizedText.value = null
        }

        fun consumeRecognizedText() {
            _recognizedText.value = null
        }

        override fun onCleared() {
            cancelProcessingFallback()
            cancelServerUpload()
            sttEngine.stopListening()
            audioRecorder.stop()?.delete()
            super.onCleared()
        }

        private fun handleSttEvent(event: SttEvent) {
            when (event) {
                is SttEvent.Started -> {
                    _status.value =
                        if (isLocalFallback) {
                            QuizRecordingStatus.FallbackRecording
                        } else {
                            QuizRecordingStatus.Recording
                        }
                }
                is SttEvent.EndOfSpeech -> moveToProcessing()
                is SttEvent.Results -> handleFinalResult(event.text)
                is SttEvent.Error -> {
                    cancelProcessingFallback()
                    _status.value = event.toRecordingStatus()
                }
                is SttEvent.Stopped -> {
                    cancelProcessingFallback()
                    if (_status.value == QuizRecordingStatus.Processing) {
                        _status.value = QuizRecordingStatus.NoSpeech
                    }
                }
                is SttEvent.PartialResults,
                is SttEvent.VolumeChanged,
                -> Unit
            }
        }

        private fun handleFinalResult(text: String) {
            cancelProcessingFallback()
            recordingMode = RecordingMode.None
            isLocalFallback = false
            val trimmedText = text.trim()
            if (trimmedText.isBlank()) {
                _status.value = QuizRecordingStatus.NoSpeech
            } else {
                _recognizedText.value = trimmedText
                _status.value = QuizRecordingStatus.Completed
            }
        }

        private fun SttEvent.Error.toRecordingStatus(): QuizRecordingStatus =
            when (type) {
                SttErrorType.PermissionRequired -> QuizRecordingStatus.PermissionError
                SttErrorType.SpeechTimeout -> QuizRecordingStatus.Timeout
                else -> QuizRecordingStatus.NoSpeech
            }

        private fun startLocalListening(isFallback: Boolean) {
            recordingMode = RecordingMode.Local
            isLocalFallback = isFallback
            _status.value =
                if (isFallback) {
                    QuizRecordingStatus.FallbackRecording
                } else {
                    QuizRecordingStatus.Recording
                }
            sttEngine.startListening(activeSessionId)
        }

        private fun stopServerRecordingAndUpload() {
            cancelProcessingFallback()
            _status.value = QuizRecordingStatus.Processing
            val audioFile = audioRecorder.stop()
            recordingMode = RecordingMode.None
            if (audioFile == null) {
                startLocalListening(isFallback = true)
                return
            }
            uploadServerStt(audioFile)
        }

        private fun uploadServerStt(audioFile: File) {
            cancelServerUpload()
            val uploadJob =
                viewModelScope.launch {
                    uploadServerSttSafely(audioFile)
                }
            serverUploadJob = uploadJob
            uploadJob.invokeOnCompletion {
                if (serverUploadJob === uploadJob) {
                    serverUploadJob = null
                }
            }
        }

        private suspend fun uploadServerSttSafely(audioFile: File) {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        translateRepository.translateSpeechToText(
                            audioFile = audioFile,
                            mimeType = SERVER_STT_AUDIO_MIME_TYPE,
                        )
                    }
                result
                    .onSuccess { response ->
                        val text =
                            response.correctedText
                                .trim()
                                .ifBlank { response.recognizedText.trim() }
                        handleFinalResult(text)
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) {
                            throw throwable
                        }
                        startLocalListening(isFallback = true)
                    }
            } finally {
                deleteTempAudio(audioFile)
            }
        }

        private fun moveToProcessing() {
            _status.value = QuizRecordingStatus.Processing
            processingFallbackJob?.cancel()
            val sessionId = activeSessionId
            processingFallbackJob =
                viewModelScope.launch {
                    delay(PROCESSING_FALLBACK_TIMEOUT_MS)
                    if (
                        activeSessionId == sessionId &&
                        _status.value == QuizRecordingStatus.Processing
                    ) {
                        _status.value = QuizRecordingStatus.NoSpeech
                    }
                }
        }

        private fun cancelProcessingFallback() {
            processingFallbackJob?.cancel()
            processingFallbackJob = null
        }

        private fun cancelServerUpload() {
            serverUploadJob?.cancel()
            serverUploadJob = null
        }

        private fun deleteTempAudio(audioFile: File) {
            if (audioFile.exists()) {
                audioFile.delete()
            }
        }

        private enum class RecordingMode {
            None,
            Server,
            Local,
        }

        companion object {
            private const val INITIAL_SESSION_ID = 0
            private const val PROCESSING_FALLBACK_TIMEOUT_MS = 3_000L
            private const val SERVER_STT_FILE_NAME = "quiz_stt_audio"
            private const val SERVER_STT_AUDIO_MIME_TYPE = "audio/wav"
        }
    }
