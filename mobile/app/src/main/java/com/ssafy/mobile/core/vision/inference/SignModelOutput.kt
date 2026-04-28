package com.ssafy.mobile.core.vision.inference

object SignModelContract {
    const val SEQUENCE_LENGTH = 30
    const val FEATURE_DIMENSION = 345
    const val CLASS_COUNT = 110
    const val CONFIDENCE_THRESHOLD = 0.85f
    val outputActivation: ModelOutputActivation = ModelOutputActivation.SOFTMAX_PROBABILITY
}

enum class ModelOutputActivation {
    SOFTMAX_PROBABILITY,
}

data class SignModelOutput(
    val probabilities: List<Float>,
) {
    init {
        require(probabilities.size == SignModelContract.CLASS_COUNT) {
            "TFLite output must contain ${SignModelContract.CLASS_COUNT} softmax probabilities."
        }
    }

    fun topPrediction(): SignModelPrediction? {
        val indexedConfidence = probabilities.withIndex().maxByOrNull { it.value } ?: return null
        return SignModelPrediction(
            classIndex = indexedConfidence.index,
            confidence = indexedConfidence.value,
            isConfident = indexedConfidence.value >= SignModelContract.CONFIDENCE_THRESHOLD,
        )
    }
}

data class SignModelPrediction(
    val classIndex: Int,
    val confidence: Float,
    val isConfident: Boolean,
)
