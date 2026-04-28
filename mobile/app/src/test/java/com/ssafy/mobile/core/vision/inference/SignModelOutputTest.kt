package com.ssafy.mobile.core.vision.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignModelOutputTest {
    @Test
    fun treatsTfliteOutputAsSoftmaxProbabilities() {
        val probabilities =
            MutableList(SignModelContract.CLASS_COUNT) { LOW_CONFIDENCE }.also {
                it[EXPECTED_CLASS_INDEX] = HIGH_CONFIDENCE
            }

        val prediction = SignModelOutput(probabilities).topPrediction()

        assertEquals(ModelOutputActivation.SOFTMAX_PROBABILITY, SignModelContract.outputActivation)
        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertEquals(HIGH_CONFIDENCE, prediction?.confidence ?: 0f, FLOAT_DELTA)
        assertTrue(prediction?.isConfident == true)
    }

    @Test
    fun marksLowTopProbabilityAsNotConfident() {
        val probabilities =
            MutableList(SignModelContract.CLASS_COUNT) { LOW_CONFIDENCE }.also {
                it[EXPECTED_CLASS_INDEX] = BELOW_THRESHOLD_CONFIDENCE
            }

        val prediction = SignModelOutput(probabilities).topPrediction()

        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertFalse(prediction?.isConfident == true)
    }

    private companion object {
        const val EXPECTED_CLASS_INDEX = 7
        const val LOW_CONFIDENCE = 0.001f
        const val HIGH_CONFIDENCE = 0.9f
        const val BELOW_THRESHOLD_CONFIDENCE = 0.5f
        const val FLOAT_DELTA = 0.0001f
    }
}
