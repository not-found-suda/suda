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
        assertTrue((prediction?.margin ?: 0f) > SignModelContract.MARGIN_THRESHOLD)
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

    @Test
    fun marksLowTopMarginAsNotConfident() {
        val probabilities =
            MutableList(SignModelContract.CLASS_COUNT) { LOW_PROBABILITY }.also {
                it[EXPECTED_CLASS_INDEX] = HIGH_PROBABILITY
                it[SECOND_CLASS_INDEX] = HIGH_PROBABILITY - LOW_MARGIN
            }

        val prediction = SignModelOutput(probabilities).topPrediction()

        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertEquals(LOW_MARGIN, prediction?.margin ?: 0f, FLOAT_DELTA)
        assertFalse(prediction?.isConfident == true)
    }

    private companion object {
        const val EXPECTED_CLASS_INDEX = 3
        const val SECOND_CLASS_INDEX = 2
        const val LOW_PROBABILITY = 0.01f
        const val HIGH_PROBABILITY = 0.9f
        const val LOW_TOP_PROBABILITY = 0.5f
        const val LOW_MARGIN = 0.05f
        const val FLOAT_DELTA = 0.0001f
    }
}
