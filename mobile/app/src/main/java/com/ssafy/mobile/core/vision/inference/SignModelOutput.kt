package com.ssafy.mobile.core.vision.inference

object SignModelContract {
    const val BATCH_SIZE = 1
    const val SEQUENCE_LENGTH = 30
    const val FEATURE_DIMENSION = 345
    const val CLASS_COUNT = 110
    const val CONFIDENCE_THRESHOLD = 0.85f
    const val FLOAT_BYTE_SIZE = 4
    const val FLAT_SEQUENCE_INPUT_SIZE = SEQUENCE_LENGTH * FEATURE_DIMENSION
    const val MODEL_ASSET_PATH = "sign_model.tflite"
    const val LABEL_MAP_ASSET_PATH = "label_map.json"
    const val UNKNOWN_GLOSS = "unknown"
    val inputShape: IntArray = intArrayOf(BATCH_SIZE, SEQUENCE_LENGTH, FEATURE_DIMENSION)
    val outputShape: IntArray = intArrayOf(BATCH_SIZE, CLASS_COUNT)
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
