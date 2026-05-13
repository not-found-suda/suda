package com.ssafy.mobile.core.vision.feature

import com.ssafy.mobile.core.vision.inference.SignModelContract
import java.util.ArrayDeque
import kotlin.math.ceil

class SignSequenceBuffer(
    private val sequenceLength: Int = DEFAULT_SEQUENCE_LENGTH,
    minimumPredictionFrames: Int = DEFAULT_MINIMUM_PREDICTION_FRAMES,
    minimumHandFrameRatio: Float = DEFAULT_MINIMUM_HAND_FRAME_RATIO,
) {
    private val frames = ArrayDeque<LandmarkFeatureFrame>(sequenceLength)
    private val minimumFrames: Int =
        minimumPredictionFrames
            .coerceIn(MINIMUM_PREDICTION_FRAMES_LOWER_BOUND, sequenceLength)
    private val minimumHandFrames: Int =
        ceil(sequenceLength * minimumHandFrameRatio)
            .toInt()
            .coerceAtLeast(MINIMUM_HAND_FRAMES)

    val size: Int
        get() = frames.size

    val handFrameCount: Int
        get() = frames.count { frame -> frame.hasHands }

    val hasEnoughFrames: Boolean
        get() = frames.size >= minimumFrames

    val hasEnoughHandFrames: Boolean
        get() = handFrameCount >= minimumHandFrames

    fun add(frame: LandmarkFeatureFrame) {
        if (frames.size == sequenceLength) {
            frames.removeFirst()
        }
        frames.addLast(frame)
    }

    fun buildSequenceInput(): FloatArray? = buildSequenceSnapshot()?.values

    fun buildSequenceSnapshot(): SignSequenceSnapshot? {
        val readyFrames = buildReadyFrames() ?: return null
        val sequence = FloatArray(sequenceLength * SignModelContract.FEATURE_DIMENSION)
        var offset = 0
        readyFrames.forEach { frame ->
            frame.values.copyInto(
                destination = sequence,
                destinationOffset = offset,
            )
            offset += SignModelContract.FEATURE_DIMENSION
        }
        return SignSequenceSnapshot(
            values = sequence,
            timestampsMs = readyFrames.map { frame -> frame.timestampMs },
        )
    }

    private fun buildReadyFrames(): List<LandmarkFeatureFrame>? {
        if (!hasEnoughFrames || !hasEnoughHandFrames) {
            return null
        }

        val readyFrames = frames.toMutableList()
        val lastFrame = readyFrames.lastOrNull()
        while (readyFrames.size < sequenceLength && lastFrame != null) {
            readyFrames.add(lastFrame)
        }
        return readyFrames
    }

    fun clear() {
        frames.clear()
    }

    companion object {
        const val DEFAULT_SEQUENCE_LENGTH = SignModelContract.SEQUENCE_LENGTH
        const val DEFAULT_MINIMUM_PREDICTION_FRAMES = 15
        const val DEFAULT_MINIMUM_HAND_FRAME_RATIO = 0f
        private const val MINIMUM_HAND_FRAMES = 1
        private const val MINIMUM_PREDICTION_FRAMES_LOWER_BOUND = 1
    }
}

class SignSequenceSnapshot(
    val values: FloatArray,
    val timestampsMs: List<Long>,
)
