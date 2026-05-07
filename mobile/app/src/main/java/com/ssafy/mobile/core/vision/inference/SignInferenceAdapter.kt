package com.ssafy.mobile.core.vision.inference

interface SignInferenceAdapter {
    fun predict(sequence: FloatArray): SignInferenceResult

    fun close()
}

data class SignInferenceResult(
    val gloss: String,
    val confidence: Float,
    val margin: Float = MAX_CONFIDENCE,
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
