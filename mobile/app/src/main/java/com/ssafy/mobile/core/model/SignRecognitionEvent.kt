package com.ssafy.mobile.core.model

sealed interface SignRecognitionEvent {
    // 단일 단어 추론 결과
    data class Prediction(
        val gloss: String,
        val confidence: Float,
        val timestampMs: Long,
    ) : SignRecognitionEvent

    // 단어들이 모여 문장이 될 때 (선택적 사용)
    data class Utterance(
        val glosses: List<String>,
        val confidence: Float,
        val startedAtMs: Long,
        val endedAtMs: Long,
    ) : SignRecognitionEvent

    data object NoHandsDetected : SignRecognitionEvent

    data object ModelLoading : SignRecognitionEvent

    data class Error(
        val message: String,
    ) : SignRecognitionEvent
}
