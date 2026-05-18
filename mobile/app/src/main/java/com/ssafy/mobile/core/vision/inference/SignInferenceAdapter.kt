package com.ssafy.mobile.core.vision.inference

import com.ssafy.mobile.core.vision.feature.LandmarkFeatureFrame

interface SignInferenceAdapter {
    val supportsFullSegmentInference: Boolean
        get() = false

    fun predict(sequence: FloatArray): SignInferenceResult

    fun predictSegment(frames: List<LandmarkFeatureFrame>): SignInferenceResult =
        error("This inference adapter does not support full-segment inference.")

    fun close()
}

data class SignInferenceResult(
    val gloss: String,
    val confidence: Float,
    val margin: Float = MAX_CONFIDENCE,
    val classIndex: Int? = null,
    val secondGloss: String? = null,
    val secondConfidence: Float? = null,
    val rawGloss: String? = null,
    val accepted: Boolean = true,
    val rejectionReason: String? = null,
    val topCandidates: List<SignInferenceCandidate> = emptyList(),
) {
    init {
        require(confidence in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "Confidence must be between $MIN_CONFIDENCE and $MAX_CONFIDENCE."
        }
        require(margin in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "Margin must be between $MIN_CONFIDENCE and $MAX_CONFIDENCE."
        }
    }

    companion object {
        const val MIN_CONFIDENCE = 0f
        const val MAX_CONFIDENCE = 1f
    }
}

data class SignInferenceCandidate(
    val rank: Int,
    val classIndex: Int,
    val gloss: String,
    val confidence: Float,
)
