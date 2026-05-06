package com.ssafy.mobile.feature.quiz.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.stt.SttEngine
import com.ssafy.mobile.core.stt.SttErrorType
import com.ssafy.mobile.core.stt.SttEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class QuizRecordingViewModel
    @Inject
    constructor(
        private val sttEngine: SttEngine,
    ) : ViewModel() {
        private val _status = MutableStateFlow(QuizRecordingStatus.Idle)
        val status: StateFlow<QuizRecordingStatus> = _status.asStateFlow()

        private val _recognizedText = MutableStateFlow<String?>(null)
        val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

        private var activeSessionId = INITIAL_SESSION_ID
        private var processingFallbackJob: Job? = null

        init {
            viewModelScope.launch {
                sttEngine.events.collect { event ->
                    if (event.sessionId == activeSessionId) {
                        handleSttEvent(event)
                    }
                }
            }
        }

        fun startListening() {
            cancelProcessingFallback()
            activeSessionId = sttEngine.nextSessionId()
            _recognizedText.value = null
            _status.value = QuizRecordingStatus.Recording
            sttEngine.startListening(activeSessionId)
        }

        fun stopListening() {
            if (_status.value == QuizRecordingStatus.Recording) {
                moveToProcessing()
                sttEngine.stopListening()
            }
        }

        fun reset() {
            activeSessionId = sttEngine.nextSessionId()
            cancelProcessingFallback()
            sttEngine.stopListening()
            _status.value = QuizRecordingStatus.Idle
            _recognizedText.value = null
        }

        fun consumeRecognizedText() {
            _recognizedText.value = null
        }

        override fun onCleared() {
            cancelProcessingFallback()
            sttEngine.stopListening()
            super.onCleared()
        }

        private fun handleSttEvent(event: SttEvent) {
            when (event) {
                is SttEvent.Started -> _status.value = QuizRecordingStatus.Recording
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

        companion object {
            private const val INITIAL_SESSION_ID = 0
            private const val PROCESSING_FALLBACK_TIMEOUT_MS = 3_000L
        }
    }
