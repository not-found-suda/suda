package com.ssafy.mobile.core.vision.inference

object SignModelContract {
    const val BATCH_SIZE = 1
    const val SEQUENCE_LENGTH = 30
    const val FEATURE_DIMENSION = 332
    const val CLASS_COUNT = 23
    const val CONFIDENCE_THRESHOLD = 0.80f
    const val MARGIN_THRESHOLD = 0.0f
    const val FLOAT_BYTE_SIZE = 4
    const val FLAT_SEQUENCE_INPUT_SIZE = SEQUENCE_LENGTH * FEATURE_DIMENSION
    val MODEL_ASSET_PATH = SignModelVariant.DEFAULT.modelAssetPath
    const val LABEL_MAP_ASSET_PATH = "models/label_map_v6.json"
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
        val rankedPredictions = rankedPredictions()
        val topPrediction = rankedPredictions.firstOrNull() ?: return null
        val secondPrediction = rankedPredictions.getOrNull(1)
        return topPrediction.withSecondPrediction(secondPrediction)
    }

    fun rankedPredictions(limit: Int = SignModelContract.CLASS_COUNT): List<SignModelPrediction> {
        val probabilities =
            when (SignModelContract.outputActivation) {
                ModelOutputActivation.LOGITS -> values.toSoftmaxProbabilities()
                ModelOutputActivation.PROBABILITIES -> values.asProbabilities()
            }
        return probabilities
            .withIndex()
            .sortedByDescending { indexedValue -> indexedValue.value }
            .take(limit)
            .map { indexedValue ->
                SignModelPrediction(
                    classIndex = indexedValue.index,
                    confidence = indexedValue.value,
                    secondClassIndex = null,
                    secondConfidence = null,
                    margin = indexedValue.value,
                    isConfident = false,
                )
            }
    }
}

data class SignModelPrediction(
    val classIndex: Int,
    val confidence: Float,
    val secondClassIndex: Int?,
    val secondConfidence: Float?,
    val margin: Float,
    val isConfident: Boolean,
)

private fun SignModelPrediction.withSecondPrediction(
    secondPrediction: SignModelPrediction?,
): SignModelPrediction {
    val margin =
        confidence -
            (secondPrediction?.confidence ?: SignInferenceResult.MIN_CONFIDENCE)
    return copy(
        secondClassIndex = secondPrediction?.classIndex,
        secondConfidence = secondPrediction?.confidence,
        margin = margin,
        isConfident =
            confidence >= SignModelContract.CONFIDENCE_THRESHOLD &&
                margin >= SignModelContract.MARGIN_THRESHOLD,
    )
}

private fun List<Float>.asProbabilities(): List<Float> {
    if (isEmpty()) return emptyList()
    val sum = sum()
    val looksLikeProbabilities =
        all { value -> value in 0f..1f } &&
            sum in PROBABILITY_SUM_LOWER_BOUND..PROBABILITY_SUM_UPPER_BOUND
    return if (looksLikeProbabilities) {
        this
    } else {
        toSoftmaxProbabilities()
    }
}

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

private const val PROBABILITY_SUM_LOWER_BOUND = 0.98f
private const val PROBABILITY_SUM_UPPER_BOUND = 1.02f
