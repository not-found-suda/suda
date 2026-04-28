package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignPredictionStabilizerTest {
    @Test
    fun ignoresLowConfidencePrediction() {
        val stabilizer = createStabilizer()

        assertNull(stabilizer.onPrediction(createPrediction(confidence = LOW_CONFIDENCE)))
    }

    @Test
    fun ignoresNoneOrUnknownPrediction() {
        val stabilizer = createStabilizer()

        assertNull(stabilizer.onPrediction(createPrediction(gloss = "none")))
        assertNull(stabilizer.onPrediction(createPrediction(gloss = "UNKNOWN")))
    }

    @Test
    fun emitsStablePredictionWhenRequiredVotesAreSatisfied() {
        val stabilizer = createStabilizer()

        assertNull(stabilizer.onPrediction(createPrediction(gloss = "엄마")))
        assertNull(stabilizer.onPrediction(createPrediction(gloss = "아빠")))
        val stablePrediction = stabilizer.onPrediction(createPrediction(gloss = "엄마"))

        assertEquals("엄마", stablePrediction?.gloss)
        assertEquals(DEFAULT_CONFIDENCE, stablePrediction?.confidence ?: 0f, FLOAT_DELTA)
    }

    @Test
    fun suppressesRepeatedSameGloss() {
        val stabilizer = createStabilizer()

        stabilizer.onPrediction(createPrediction(gloss = "엄마"))
        stabilizer.onPrediction(createPrediction(gloss = "아빠"))
        stabilizer.onPrediction(createPrediction(gloss = "엄마"))
        stabilizer.onPrediction(createPrediction(gloss = "엄마"))

        assertNull(stabilizer.onPrediction(createPrediction(gloss = "엄마")))
    }

    @Test
    fun allowsSameGlossAfterReset() {
        val stabilizer = createStabilizer()

        stabilizer.onPrediction(createPrediction(gloss = "엄마"))
        stabilizer.onPrediction(createPrediction(gloss = "아빠"))
        stabilizer.onPrediction(createPrediction(gloss = "엄마"))
        stabilizer.reset()
        stabilizer.onPrediction(createPrediction(gloss = "엄마"))
        stabilizer.onPrediction(createPrediction(gloss = "아빠"))
        val stablePrediction = stabilizer.onPrediction(createPrediction(gloss = "엄마"))

        assertEquals("엄마", stablePrediction?.gloss)
    }

    private fun createStabilizer(): SignPredictionStabilizer =
        SignPredictionStabilizer(
            confidenceThreshold = CONFIDENCE_THRESHOLD,
            windowSize = WINDOW_SIZE,
            requiredVotes = REQUIRED_VOTES,
        )

    private fun createPrediction(
        gloss: String = "엄마",
        confidence: Float = DEFAULT_CONFIDENCE,
    ): SignInferenceResult =
        SignInferenceResult(
            gloss = gloss,
            confidence = confidence,
        )

    private companion object {
        const val CONFIDENCE_THRESHOLD = 0.85f
        const val DEFAULT_CONFIDENCE = 0.9f
        const val LOW_CONFIDENCE = 0.84f
        const val FLOAT_DELTA = 0.0001f
        const val WINDOW_SIZE = 5
        const val REQUIRED_VOTES = 2
    }
}
