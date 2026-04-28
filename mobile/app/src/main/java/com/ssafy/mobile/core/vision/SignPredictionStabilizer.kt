package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.inference.SignModelContract
import java.util.ArrayDeque

class SignPredictionStabilizer(
    private val confidenceThreshold: Float = SignModelContract.CONFIDENCE_THRESHOLD,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val requiredVotes: Int = DEFAULT_REQUIRED_VOTES,
    private val ignoredGlosses: Set<String> = DEFAULT_IGNORED_GLOSSES,
) {
    private val recentPredictions = ArrayDeque<SignInferenceResult>(windowSize)
    private var lastEmittedGloss: String? = null

    init {
        require(confidenceThreshold in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "신뢰도 기준값은 $MIN_CONFIDENCE 이상 $MAX_CONFIDENCE 이하이어야 합니다."
        }
        require(windowSize > 0) {
            "예측 결과 window 크기는 1 이상이어야 합니다."
        }
        require(requiredVotes in 1..windowSize) {
            "확정에 필요한 vote 수는 1 이상 window 크기 이하이어야 합니다."
        }
    }

    fun onPrediction(result: SignInferenceResult): StableSignPrediction? {
        var stablePrediction: StableSignPrediction? = null
        if (isUsable(result)) {
            addPrediction(result)
            val candidate = findStableCandidate()
            if (candidate != null && candidate.gloss != lastEmittedGloss) {
                recentPredictions.clear()
                lastEmittedGloss = candidate.gloss
                stablePrediction = candidate
            }
        }

        return stablePrediction
    }

    fun reset() {
        recentPredictions.clear()
        lastEmittedGloss = null
    }

    private fun isUsable(result: SignInferenceResult): Boolean =
        result.confidence >= confidenceThreshold &&
            result.gloss.trim().lowercase() !in ignoredGlosses

    private fun addPrediction(result: SignInferenceResult) {
        if (recentPredictions.size == windowSize) {
            recentPredictions.removeFirst()
        }
        recentPredictions.addLast(result)
    }

    private fun findStableCandidate(): StableSignPrediction? =
        recentPredictions
            .groupBy { prediction -> prediction.gloss }
            .mapValues { (_, predictions) ->
                PredictionVote(
                    gloss = predictions.first().gloss,
                    count = predictions.size,
                    confidence =
                        predictions
                            .map { prediction -> prediction.confidence }
                            .average()
                            .toFloat(),
                )
            }.values
            .filter { vote -> vote.count >= requiredVotes }
            .maxWithOrNull(
                compareBy<PredictionVote> { vote -> vote.count }
                    .thenBy { vote -> vote.confidence },
            )?.let { vote ->
                StableSignPrediction(
                    gloss = vote.gloss,
                    confidence = vote.confidence,
                )
            }

    private data class PredictionVote(
        val gloss: String,
        val count: Int,
        val confidence: Float,
    )

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 5
        const val DEFAULT_REQUIRED_VOTES = 3
        const val MIN_CONFIDENCE = 0f
        const val MAX_CONFIDENCE = 1f
        val DEFAULT_IGNORED_GLOSSES = setOf("none", "unknown", "<none>", "<unknown>")
    }
}

data class StableSignPrediction(
    val gloss: String,
    val confidence: Float,
)
