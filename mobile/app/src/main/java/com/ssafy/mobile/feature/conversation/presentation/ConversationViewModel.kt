package com.ssafy.mobile.feature.conversation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.SignRecognitionEngine
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
    ) : ViewModel() {
        private val _sessionState = MutableStateFlow(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private val _lastGlosses = MutableStateFlow<List<String>>(emptyList())
        val lastGlosses: StateFlow<List<String>> = _lastGlosses.asStateFlow()

        private val _translatedText = MutableStateFlow("")
        val translatedText: StateFlow<String> = _translatedText.asStateFlow()

        private var translationJob: Job? = null
        private var completionTimerJob: Job? = null

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
                            // 번역 성공 시에만 단어 리스트 초기화
                            _lastGlosses.value = emptyList()
                        }.onFailure {
                            // 향후 사용자에게 번역 실패 알림 제공 로직 추가 예정
                        }
                }
        }

        fun startSession() {
            _sessionState.value = SessionState.Active
            signRecognitionEngine.start()
        }

        fun stopSession() {
            _sessionState.value = SessionState.Idle
            signRecognitionEngine.stop()
            _lastGlosses.value = emptyList()
            _translatedText.value = ""
            translationJob?.cancel()
            completionTimerJob?.cancel()
        }

        companion object {
            private const val COMPLETION_THRESHOLD_MS = 2000L
        }
    }
