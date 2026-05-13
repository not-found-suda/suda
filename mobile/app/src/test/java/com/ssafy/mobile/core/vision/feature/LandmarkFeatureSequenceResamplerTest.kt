package com.ssafy.mobile.core.vision.feature

import com.ssafy.mobile.core.vision.inference.SignModelContract
import org.junit.Assert.assertEquals
import org.junit.Test

class LandmarkFeatureSequenceResamplerTest {
    @Test
    fun returnsZerosWhenNoFramesAreAvailable() {
        val sequence =
            LandmarkFeatureSequenceResampler.resampleToModelInput(
                frames = emptyList(),
                sequenceLength = TEST_SEQUENCE_LENGTH,
            )

        assertEquals(TEST_SEQUENCE_LENGTH * SignModelContract.FEATURE_DIMENSION, sequence.size)
        assertEquals(0f, sequence[0], FLOAT_DELTA)
        assertEquals(0f, sequence.last(), FLOAT_DELTA)
    }

    @Test
    fun repeatsSingleFrameAcrossSequence() {
        val sequence =
            LandmarkFeatureSequenceResampler.resampleToModelInput(
                frames = listOf(createFeatureFrame(7f)),
                sequenceLength = TEST_SEQUENCE_LENGTH,
            )

        assertEquals(7f, sequence[0], FLOAT_DELTA)
        assertEquals(7f, sequence[SignModelContract.FEATURE_DIMENSION], FLOAT_DELTA)
        assertEquals(7f, sequence[SignModelContract.FEATURE_DIMENSION * 2], FLOAT_DELTA)
    }

    @Test
    fun linearlyResamplesFramesToTargetLength() {
        val sequence =
            LandmarkFeatureSequenceResampler.resampleToModelInput(
                frames =
                    listOf(
                        createFeatureFrame(0f),
                        createFeatureFrame(10f),
                        createFeatureFrame(20f),
                    ),
                sequenceLength = FIVE_FRAME_SEQUENCE_LENGTH,
            )

        assertEquals(0f, sequence[0], FLOAT_DELTA)
        assertEquals(5f, sequence[SignModelContract.FEATURE_DIMENSION], FLOAT_DELTA)
        assertEquals(10f, sequence[SignModelContract.FEATURE_DIMENSION * 2], FLOAT_DELTA)
        assertEquals(15f, sequence[SignModelContract.FEATURE_DIMENSION * 3], FLOAT_DELTA)
        assertEquals(20f, sequence[SignModelContract.FEATURE_DIMENSION * 4], FLOAT_DELTA)
    }

    private fun createFeatureFrame(value: Float): LandmarkFeatureFrame =
        LandmarkFeatureFrame(
            timestampMs = 0L,
            values = FloatArray(SignModelContract.FEATURE_DIMENSION) { value },
        )

    private companion object {
        const val TEST_SEQUENCE_LENGTH = 3
        const val FIVE_FRAME_SEQUENCE_LENGTH = 5
        const val FLOAT_DELTA = 0.0001f
    }
}
