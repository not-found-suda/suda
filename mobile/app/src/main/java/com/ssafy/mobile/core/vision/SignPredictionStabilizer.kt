package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.inference.SignModelContract
import java.util.ArrayDeque

class SignPredictionStabilizer(
    private val confidenceThreshold: Float = SignModelContract.CONFIDENCE_THRESHOLD,
    private val marginThreshold: Float = SignModelContract.MARGIN_THRESHOLD,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val requiredVotes: Int = DEFAULT_REQUIRED_VOTES,
    private val emitCooldownMs: Long = DEFAULT_EMIT_COOLDOWN_MS,
    private val ignoredGlosses: Set<String> = DEFAULT_IGNORED_GLOSSES,
) {
    private val recentPredictions = ArrayDeque<SignInferenceResult>(windowSize)
    private var lastEmittedGloss: String? = null
    private var lastEmittedAtMs: Long? = null

    init {
        require(confidenceThreshold in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "Confidence threshold must be between $MIN_CONFIDENCE and $MAX_CONFIDENCE."
        }
        require(marginThreshold in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "Margin threshold must be between $MIN_CONFIDENCE and $MAX_CONFIDENCE."
        }
        require(windowSize > 0) {
            "Prediction window size must be greater than 0."
        }
        require(requiredVotes in 1..windowSize) {
            "Required votes must be between 1 and the prediction window size."
        }
        require(emitCooldownMs >= 0L) {
            "Prediction emit cooldown must be 0 or greater."
        }
    }

    fun onPrediction(
        result: SignInferenceResult,
        timestampMs: Long = Long.MAX_VALUE,
    ): StableSignPrediction? {
        var stablePrediction: StableSignPrediction? = null
        if (canVote(result)) {
            addPrediction(result)
            val candidate = findStableCandidate()
            if (
                candidate != null &&
                candidate.gloss != lastEmittedGloss &&
                canEmitAt(timestampMs)
            ) {
                lastEmittedGloss = candidate.gloss
                lastEmittedAtMs = timestampMs
                recentPredictions.clear()
                stablePrediction = candidate
            }
        } else {
            recentPredictions.clear()
            lastEmittedGloss = null
        }

        return stablePrediction
    }

    fun reset() {
        recentPredictions.clear()
        lastEmittedGloss = null
        lastEmittedAtMs = null
    }

    private fun canEmitAt(timestampMs: Long): Boolean {
        val previousEmittedAtMs = lastEmittedAtMs
        return timestampMs == Long.MAX_VALUE ||
            previousEmittedAtMs == null ||
            timestampMs - previousEmittedAtMs >= emitCooldownMs
    }

    private fun canVote(result: SignInferenceResult): Boolean =
        result.confidence >= confidenceThreshold &&
            result.margin >= marginThreshold &&
            result.gloss.trim().lowercase() !in ignoredGlosses

    private fun addPrediction(result: SignInferenceResult) {
        if (recentPredictions.size == windowSize) {
            recentPredictions.removeFirst()
        }
        recentPredictions.addLast(result)
    }

    private fun findStableCandidate(): StableSignPrediction? {
        val tailPredictions = recentPredictions.toList().takeLast(requiredVotes)
        val candidateGloss = tailPredictions.lastOrNull()?.gloss
        val isStable =
            candidateGloss != null &&
                tailPredictions.size == requiredVotes &&
                tailPredictions.all { prediction -> prediction.gloss == candidateGloss } &&
                candidateGloss.trim().lowercase() !in ignoredGlosses

        return if (isStable && candidateGloss != null) {
            StableSignPrediction(
                gloss = candidateGloss,
                confidence =
                    tailPredictions
                        .map { prediction -> prediction.confidence }
                        .average()
                        .toFloat(),
            )
        } else {
            null
        }
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 5
        const val DEFAULT_REQUIRED_VOTES = 3
        const val DEFAULT_EMIT_COOLDOWN_MS = 0L
        const val MIN_CONFIDENCE = 0f
        const val MAX_CONFIDENCE = 1f
        const val NONE_GLOSS = "none"
        val DEFAULT_IGNORED_GLOSSES = setOf("none", "unknown", "<none>", "<unknown>")
    }
}

data class StableSignPrediction(
    val gloss: String,
    val confidence: Float,
)
