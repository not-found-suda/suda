package com.ssafy.mobile.core.vision.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignModelOutputTest {
    @Test
    fun convertsTfliteLogitsToSoftmaxProbabilities() {
        val logits =
            MutableList(SignModelContract.CLASS_COUNT) { LOW_LOGIT }.also {
                it[EXPECTED_CLASS_INDEX] = HIGH_LOGIT
            }

        val prediction = SignModelOutput(logits).topPrediction()

        assertEquals(ModelOutputActivation.LOGITS, SignModelContract.outputActivation)
        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertTrue((prediction?.confidence ?: 0f) > SignModelContract.CONFIDENCE_THRESHOLD)
        assertTrue(prediction?.isConfident == true)
    }

    @Test
    fun marksLowTopLogitSoftmaxProbabilityAsNotConfident() {
        val logits =
            MutableList(SignModelContract.CLASS_COUNT) { LOW_LOGIT }.also {
                it[EXPECTED_CLASS_INDEX] = SLIGHTLY_HIGHER_LOGIT
            }

        val prediction = SignModelOutput(logits).topPrediction()

        assertEquals(EXPECTED_CLASS_INDEX, prediction?.classIndex)
        assertFalse(prediction?.isConfident == true)
    }

    private companion object {
        const val EXPECTED_CLASS_INDEX = 7
        const val LOW_LOGIT = 0f
        const val HIGH_LOGIT = 10f
        const val SLIGHTLY_HIGHER_LOGIT = 1f
    }
}
