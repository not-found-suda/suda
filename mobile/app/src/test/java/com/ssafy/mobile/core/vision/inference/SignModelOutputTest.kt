package com.ssafy.mobile.core.vision.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignModelOutputTest {
    @Test
    fun readsProbabilitiesAsPredictionConfidence() {
        val probabilities =
            MutableList(SignModelContract.CLASS_COUNT) {
                (1f - HIGH_PROBABILITY) / (SignModelContract.CLASS_COUNT - 1)
            }.also {
                it[EXPECTED_CLASS_INDEX] = HIGH_PROBABILITY
            }

        val prediction = SignModelOutput(probabilities).topPrediction()

        assertEquals(ModelOutputActivation.PROBABILITIES, SignModelContract.outputActivation)
        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertTrue((prediction?.confidence ?: 0f) > SignModelContract.CONFIDENCE_THRESHOLD)
        assertTrue((prediction?.margin ?: 0f) > SignModelContract.MARGIN_THRESHOLD)
        assertTrue(prediction?.isConfident == true)
    }

    @Test
    fun marksLowTopConfidenceAsNotConfident() {
        val probabilities = MutableList(SignModelContract.CLASS_COUNT) { FLAT_PROBABILITY }

        val prediction = SignModelOutput(probabilities).topPrediction()

        assertEquals(0, prediction?.classIndex)
        assertFalse(prediction?.isConfident == true)
    }

    private companion object {
        const val EXPECTED_CLASS_INDEX = 3
        const val HIGH_PROBABILITY = 0.9f
        const val FLAT_PROBABILITY = 0.0f
    }
}
