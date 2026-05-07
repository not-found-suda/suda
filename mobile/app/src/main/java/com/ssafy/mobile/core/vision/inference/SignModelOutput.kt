package com.ssafy.mobile.core.vision.inference

object SignModelContract {
    const val BATCH_SIZE = 1
    const val SEQUENCE_LENGTH = 30
    const val FEATURE_DIMENSION = 332
    const val CLASS_COUNT = 7
    const val CONFIDENCE_THRESHOLD = 0.75f
    const val MARGIN_THRESHOLD = 0.08f
    const val FLOAT_BYTE_SIZE = 4
    const val FLAT_SEQUENCE_INPUT_SIZE = SEQUENCE_LENGTH * FEATURE_DIMENSION
    val MODEL_ASSET_PATH = SignModelVariant.DEFAULT.modelAssetPath
    const val LABEL_MAP_ASSET_PATH = "models/label_map_v5_1.json"
    const val UNKNOWN_GLOSS = "unknown"
    val inputShape: IntArray = intArrayOf(BATCH_SIZE, SEQUENCE_LENGTH, FEATURE_DIMENSION)
    val outputShape: IntArray = intArrayOf(BATCH_SIZE, CLASS_COUNT)
    val outputActivation: ModelOutputActivation = ModelOutputActivation.PROBABILITIES
}

enum class ModelOutputActivation {
    LOGITS,
    PROBABILITIES,
}

data class SignModelOutput(
    val values: List<Float>,
) {
    init {
        require(values.size == SignModelContract.CLASS_COUNT) {
            "TFLite output must contain ${SignModelContract.CLASS_COUNT} class values."
        }
    }

    fun topPrediction(): SignModelPrediction? {
        val probabilities =
            when (SignModelContract.outputActivation) {
                ModelOutputActivation.LOGITS -> values.toSoftmaxProbabilities()
                ModelOutputActivation.PROBABILITIES -> values
            }
        val rankedProbabilities =
            probabilities
                .withIndex()
                .sortedByDescending { indexedValue -> indexedValue.value }
        val indexedConfidence = rankedProbabilities.firstOrNull() ?: return null
        val nextConfidence =
            rankedProbabilities.getOrNull(1)?.value
                ?: SignInferenceResult.MIN_CONFIDENCE
        val margin = indexedConfidence.value - nextConfidence
        return SignModelPrediction(
            classIndex = indexedConfidence.index,
            confidence = indexedConfidence.value,
            margin = margin,
            isConfident =
                indexedConfidence.value >= SignModelContract.CONFIDENCE_THRESHOLD &&
                    margin >= SignModelContract.MARGIN_THRESHOLD,
        )
    }
}

data class SignModelPrediction(
    val classIndex: Int,
    val confidence: Float,
    val margin: Float,
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
