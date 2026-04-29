package com.ssafy.mobile.core.vision.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SignInferenceAssetStateTest {
    @Test
    fun usesFakeWhenModelAndLabelMapAreBothMissing() {
        val policy =
            SignInferenceAssetState(
                hasModel = false,
                hasLabelMap = false,
            ).resolvePolicy()

        assertEquals(SignInferenceAdapterPolicy.FAKE, policy)
    }

    @Test
    fun usesTfliteWhenModelAndLabelMapAreBothPresent() {
        val policy =
            SignInferenceAssetState(
                hasModel = true,
                hasLabelMap = true,
            ).resolvePolicy()

        assertEquals(SignInferenceAdapterPolicy.TFLITE, policy)
    }

    @Test
    fun rejectsModelWithoutLabelMap() {
        assertThrows(IllegalStateException::class.java) {
            SignInferenceAssetState(
                hasModel = true,
                hasLabelMap = false,
            ).resolvePolicy()
        }
    }

    @Test
    fun rejectsLabelMapWithoutModel() {
        assertThrows(IllegalStateException::class.java) {
            SignInferenceAssetState(
                hasModel = false,
                hasLabelMap = true,
            ).resolvePolicy()
        }
    }
}
