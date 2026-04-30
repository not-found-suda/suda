package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignPredictionStabilizerTest {
    @Test
    fun treatsLowConfidencePredictionAsNone() {
        val stabilizer = createStabilizer()

        assertNull(stabilizer.onPrediction(createPrediction(confidence = LOW_CONFIDENCE)))
    }

    @Test
    fun treatsNoneOrUnknownPredictionAsNone() {
        val stabilizer = createStabilizer()

        assertNull(stabilizer.onPrediction(createPrediction(gloss = "none")))
        assertNull(stabilizer.onPrediction(createPrediction(gloss = "UNKNOWN")))
    }

    @Test
    fun waitsForRequiredVotesBeforeEmittingCurrentModePrediction() {
        val stabilizer = createStabilizer()

        assertNull(stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS)))
        assertNull(stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS)))
        val stablePrediction = stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS))

        assertEquals(TEST_GLOSS, stablePrediction?.gloss)
        assertEquals(DEFAULT_CONFIDENCE, stablePrediction?.confidence ?: 0f, FLOAT_DELTA)
    }

    @Test
    fun suppressesRepeatedSameGloss() {
        val stabilizer = createStabilizer()

        repeat(REQUIRED_VOTES) {
            stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS))
        }

        assertNull(stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS)))
    }

    @Test
    fun suppressesDifferentGlossDuringEmitCooldown() {
        val stabilizer =
            SignPredictionStabilizer(
                confidenceThreshold = CONFIDENCE_THRESHOLD,
                windowSize = WINDOW_SIZE,
                requiredVotes = REQUIRED_VOTES,
                emitCooldownMs = EMIT_COOLDOWN_MS,
            )

        repeat(REQUIRED_VOTES) { index ->
            stabilizer.onPrediction(
                result = createPrediction(gloss = TEST_GLOSS),
                timestampMs = index.toLong(),
            )
        }

        repeat(REQUIRED_VOTES) { index ->
            val stablePrediction =
                stabilizer.onPrediction(
                    result = createPrediction(gloss = OTHER_GLOSS),
                    timestampMs = REQUIRED_VOTES + index.toLong(),
                )
            assertNull(stablePrediction)
        }
    }

    @Test
    fun allowsSameGlossAfterReset() {
        val stabilizer = createStabilizer()

        repeat(REQUIRED_VOTES) {
            stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS))
        }
        stabilizer.reset()
        repeat(REQUIRED_VOTES - 1) {
            stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS))
        }
        val stablePrediction =
            stabilizer.onPrediction(createPrediction(gloss = TEST_GLOSS))

        assertEquals(TEST_GLOSS, stablePrediction?.gloss)
    }

    private fun createStabilizer(): SignPredictionStabilizer =
        SignPredictionStabilizer(
            confidenceThreshold = CONFIDENCE_THRESHOLD,
            windowSize = WINDOW_SIZE,
            requiredVotes = REQUIRED_VOTES,
        )

    private fun createPrediction(
        gloss: String = TEST_GLOSS,
        confidence: Float = DEFAULT_CONFIDENCE,
    ): SignInferenceResult =
        SignInferenceResult(
            gloss = gloss,
            confidence = confidence,
        )

    private companion object {
        const val TEST_GLOSS = "mom"
        const val OTHER_GLOSS = "dad"
        const val CONFIDENCE_THRESHOLD = 0.8f
        const val DEFAULT_CONFIDENCE = 0.9f
        const val LOW_CONFIDENCE = 0.79f
        const val FLOAT_DELTA = 0.0001f
        const val WINDOW_SIZE = 5
        const val REQUIRED_VOTES = 3
        const val EMIT_COOLDOWN_MS = 1_000L
    }
}
