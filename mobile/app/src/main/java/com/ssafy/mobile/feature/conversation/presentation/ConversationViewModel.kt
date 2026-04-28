package com.ssafy.mobile.feature.conversation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    ) : ViewModel() {
        private val _sessionState = MutableStateFlow(SessionState.Idle)
        val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private val _lastGlosses = MutableStateFlow<List<String>>(emptyList())
        val lastGlosses: StateFlow<List<String>> = _lastGlosses.asStateFlow()

        init {
            // 엔진으로부터 들어오는 수어 인식 이벤트를 구독합니다.
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
                    _lastGlosses.value = _lastGlosses.value + event.gloss
                }
                is SignRecognitionEvent.NoHandsDetected -> {
                    // 필요 시 사용자에게 손이 보이지 않는다는 피드백 제공 로직 추가 가능
                }
                else -> { /* 기타 상태(로딩 등) 처리 */ }
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
        }
    }
