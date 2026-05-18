package com.ssafy.mobile.core.vision.feature

import com.ssafy.mobile.core.vision.inference.SignModelContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignSequenceBufferTest {
    @Test
    fun returnsNullUntilSequenceLengthIsSatisfied() {
        val buffer =
            SignSequenceBuffer(
                sequenceLength = TEST_SEQUENCE_LENGTH,
                minimumPredictionFrames = TEST_SEQUENCE_LENGTH,
            )

        buffer.add(createFeatureFrame(value = 1f))

        assertFalse(buffer.hasEnoughFrames)
        assertNull(buffer.buildSequenceInput())
    }

    @Test
    fun buildsSequenceInputWhenEnoughFramesAreBuffered() {
        val buffer =
            SignSequenceBuffer(
                sequenceLength = TEST_SEQUENCE_LENGTH,
                minimumPredictionFrames = TEST_SEQUENCE_LENGTH,
            )

        buffer.add(createFeatureFrame(value = 1f))
        buffer.add(createFeatureFrame(value = 2f))
        buffer.add(createFeatureFrame(value = 3f))

        val sequence = requireNotNull(buffer.buildSequenceInput())

        assertTrue(buffer.hasEnoughFrames)
        assertTrue(buffer.hasEnoughHandFrames)
        assertEquals(TEST_SEQUENCE_LENGTH * SignModelContract.FEATURE_DIMENSION, sequence.size)
        assertEquals(1f, sequence[0], FLOAT_DELTA)
        assertEquals(2f, sequence[SignModelContract.FEATURE_DIMENSION], FLOAT_DELTA)
        assertEquals(3f, sequence[SignModelContract.FEATURE_DIMENSION * 2], FLOAT_DELTA)
    }

    @Test
    fun padsLastFrameWhenMinimumPredictionFramesAreBuffered() {
        val buffer =
            SignSequenceBuffer(
                sequenceLength = TEST_SEQUENCE_LENGTH,
                minimumPredictionFrames = TEST_MINIMUM_PREDICTION_FRAMES,
            )

        buffer.add(createFeatureFrame(value = 1f, timestampMs = 1_000L))
        buffer.add(createFeatureFrame(value = 2f, timestampMs = 2_000L))

        val snapshot = requireNotNull(buffer.buildSequenceSnapshot())
        val sequence = snapshot.values

        assertTrue(buffer.hasEnoughFrames)
        assertEquals(TEST_SEQUENCE_LENGTH * SignModelContract.FEATURE_DIMENSION, sequence.size)
        assertEquals(1f, sequence[0], FLOAT_DELTA)
        assertEquals(2f, sequence[SignModelContract.FEATURE_DIMENSION], FLOAT_DELTA)
        assertEquals(2f, sequence[SignModelContract.FEATURE_DIMENSION * 2], FLOAT_DELTA)
        assertEquals(listOf(1_000L, 2_000L, 2_000L), snapshot.timestampsMs)
    }

    @Test
    fun returnsNullWhenBufferedSequenceHasTooFewHandFrames() {
        val buffer =
            SignSequenceBuffer(
                sequenceLength = TEST_SEQUENCE_LENGTH,
                minimumPredictionFrames = TEST_SEQUENCE_LENGTH,
            )

        buffer.add(createFeatureFrame(value = 1f, hasHands = false))
        buffer.add(createFeatureFrame(value = 2f, hasHands = false))
        buffer.add(createFeatureFrame(value = 3f, hasHands = false))

        assertTrue(buffer.hasEnoughFrames)
        assertFalse(buffer.hasEnoughHandFrames)
        assertNull(buffer.buildSequenceInput())
    }

    @Test
    fun keepsOnlyRecentFrames() {
        val buffer =
            SignSequenceBuffer(
                sequenceLength = TEST_SEQUENCE_LENGTH,
                minimumPredictionFrames = TEST_SEQUENCE_LENGTH,
            )

        buffer.add(createFeatureFrame(value = 1f))
        buffer.add(createFeatureFrame(value = 2f))
        buffer.add(createFeatureFrame(value = 3f))
        buffer.add(createFeatureFrame(value = 4f))

        val sequence = requireNotNull(buffer.buildSequenceInput())

        assertEquals(TEST_SEQUENCE_LENGTH, buffer.size)
        assertEquals(2f, sequence[0], FLOAT_DELTA)
        assertEquals(4f, sequence[SignModelContract.FEATURE_DIMENSION * 2], FLOAT_DELTA)
    }

    @Test
    fun clearRemovesBufferedFrames() {
        val buffer =
            SignSequenceBuffer(
                sequenceLength = TEST_SEQUENCE_LENGTH,
                minimumPredictionFrames = TEST_SEQUENCE_LENGTH,
            )
        buffer.add(createFeatureFrame(value = 1f))
        buffer.add(createFeatureFrame(value = 2f))
        buffer.add(createFeatureFrame(value = 3f))

        buffer.clear()

        assertEquals(0, buffer.size)
        assertFalse(buffer.hasEnoughFrames)
        assertNull(buffer.buildSequenceInput())
    }

    private fun createFeatureFrame(
        value: Float,
        timestampMs: Long = TIMESTAMP_MS,
        hasHands: Boolean = true,
    ): LandmarkFeatureFrame =
        LandmarkFeatureFrame(
            timestampMs = timestampMs,
            values = FloatArray(SignModelContract.FEATURE_DIMENSION) { value },
            hasHands = hasHands,
        )

    private companion object {
        const val TEST_SEQUENCE_LENGTH = 3
        const val TEST_MINIMUM_PREDICTION_FRAMES = 2
        const val TIMESTAMP_MS = 1_000L
        const val FLOAT_DELTA = 0.0001f
    }
}
