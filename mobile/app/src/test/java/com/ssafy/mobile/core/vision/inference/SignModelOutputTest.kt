package com.ssafy.mobile.core.vision.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignModelOutputTest {
    @Test
    fun usesTfliteProbabilitiesAsPredictionConfidence() {
        val probabilities =
            MutableList(SignModelContract.CLASS_COUNT) { LOW_PROBABILITY }.also {
                it[EXPECTED_CLASS_INDEX] = HIGH_PROBABILITY
            }

        val prediction = SignModelOutput(probabilities).topPrediction()

        assertEquals(ModelOutputActivation.PROBABILITIES, SignModelContract.outputActivation)
        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertTrue((prediction?.confidence ?: 0f) > SignModelContract.CONFIDENCE_THRESHOLD)
        assertTrue(prediction?.isConfident == true)
    }

    @Test
    fun marksLowTopProbabilityAsNotConfident() {
        val probabilities =
            MutableList(SignModelContract.CLASS_COUNT) { LOW_PROBABILITY }.also {
                it[EXPECTED_CLASS_INDEX] = LOW_TOP_PROBABILITY
            }

        val prediction = SignModelOutput(probabilities).topPrediction()

        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertFalse(prediction?.isConfident == true)
    }

    private companion object {
        const val EXPECTED_CLASS_INDEX = 3
        const val LOW_PROBABILITY = 0.01f
        const val HIGH_PROBABILITY = 0.9f
        const val LOW_TOP_PROBABILITY = 0.5f
    }
}
