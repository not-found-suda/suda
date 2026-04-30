package com.ssafy.mobile.core.vision.inference

object SignModelContract {
    const val BATCH_SIZE = 1
    const val SEQUENCE_LENGTH = 30
    const val FEATURE_DIMENSION = 141
    const val CLASS_COUNT = 90
    const val CONFIDENCE_THRESHOLD = 0.8f
    const val FLOAT_BYTE_SIZE = 4
    const val FLAT_SEQUENCE_INPUT_SIZE = SEQUENCE_LENGTH * FEATURE_DIMENSION
    const val MODEL_ASSET_PATH = "models/sign_model.tflite"
    const val LABEL_MAP_ASSET_PATH = "models/label_map.json"
    const val UNKNOWN_GLOSS = "unknown"
    val inputShape: IntArray = intArrayOf(BATCH_SIZE, SEQUENCE_LENGTH, FEATURE_DIMENSION)
    val outputShape: IntArray = intArrayOf(BATCH_SIZE, CLASS_COUNT)
    val outputActivation: ModelOutputActivation = ModelOutputActivation.LOGITS
}

enum class ModelOutputActivation {
    LOGITS,
}

data class SignModelOutput(
    val logits: List<Float>,
) {
    init {
        require(logits.size == SignModelContract.CLASS_COUNT) {
            "TFLite output must contain ${SignModelContract.CLASS_COUNT} class logits."
        }
    }

    fun topPrediction(): SignModelPrediction? {
        val probabilities = logits.toSoftmaxProbabilities()
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

private fun List<Float>.toSoftmaxProbabilities(): List<Float> {
    val maxLogit = maxOrNull() ?: 0f
    val expValues = map { value -> kotlin.math.exp((value - maxLogit).toDouble()) }
    val expSum = expValues.sum()
    return when {
        isEmpty() -> emptyList()
        expSum <= 0.0 -> List(size) { 0f }
        else -> expValues.map { value -> (value / expSum).toFloat() }
    }
}
