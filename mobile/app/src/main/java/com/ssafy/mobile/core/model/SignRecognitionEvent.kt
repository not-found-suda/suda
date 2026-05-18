package com.ssafy.mobile.core.model

import com.ssafy.mobile.core.vision.SignRecognitionConfig

sealed interface SignRecognitionEvent {
    data object Ready : SignRecognitionEvent

    data object Started : SignRecognitionEvent

    data object Stopped : SignRecognitionEvent

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
        val sentenceType: String? = null,
    ) : SignRecognitionEvent

    data object NoHandsDetected : SignRecognitionEvent

    data object ModelLoading : SignRecognitionEvent

    data class Metrics(
        val snapshot: SignRecognitionMetrics,
    ) : SignRecognitionEvent

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : SignRecognitionEvent
}

data class SignRecognitionMetrics(
    val timestampMs: Long,
    val currentGloss: String? = null,
    val confidence: Float? = null,
    val classIndex: Int? = null,
    val secondGloss: String? = null,
    val secondConfidence: Float? = null,
    val margin: Float? = null,
    val hasHands: Boolean = false,
    val poseLandmarkCount: Int = 0,
    val leftHandLandmarkCount: Int = 0,
    val rightHandLandmarkCount: Int = 0,
    val lipLandmarkCount: Int = 0,
    val sequenceFrameCount: Int = 0,
    val sequenceHandFrameCount: Int = 0,
    val sequenceTimestampsMs: List<Long> = emptyList(),
    val tfliteInferenceMs: Double? = null,
    val featureProbe: SignFeatureProbeSlices? = null,
    val config: SignRecognitionConfig = SignRecognitionConfig(),
)

data class SignFeatureProbeSlices(
    val head: List<Float>,
    val faceStart: List<Float>,
    val poseStart: List<Float>,
    val distanceStart: List<Float>,
)
