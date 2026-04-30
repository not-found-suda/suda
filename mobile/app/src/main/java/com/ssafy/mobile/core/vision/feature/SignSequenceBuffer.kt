package com.ssafy.mobile.core.vision.feature

import com.ssafy.mobile.core.vision.inference.SignModelContract
import java.util.ArrayDeque
import kotlin.math.ceil

class SignSequenceBuffer(
    private val sequenceLength: Int = DEFAULT_SEQUENCE_LENGTH,
    minimumHandFrameRatio: Float = DEFAULT_MINIMUM_HAND_FRAME_RATIO,
) {
    private val frames = ArrayDeque<LandmarkFeatureFrame>(sequenceLength)
    private val minimumHandFrames: Int =
        ceil(sequenceLength * minimumHandFrameRatio)
            .toInt()
            .coerceAtLeast(MINIMUM_HAND_FRAMES)

    val size: Int
        get() = frames.size

    val handFrameCount: Int
        get() = frames.count { frame -> frame.hasHands }

    val hasEnoughFrames: Boolean
        get() = frames.size == sequenceLength

    val hasEnoughHandFrames: Boolean
        get() = handFrameCount >= minimumHandFrames

    fun add(frame: LandmarkFeatureFrame) {
        if (frames.size == sequenceLength) {
            frames.removeFirst()
        }
        frames.addLast(frame)
    }

    fun buildSequenceInput(): FloatArray? {
        if (!hasEnoughFrames || !hasEnoughHandFrames) {
            return null
        }

        val sequence = FloatArray(sequenceLength * SignModelContract.FEATURE_DIMENSION)
        var offset = 0
        frames.forEach { frame ->
            frame.values.copyInto(
                destination = sequence,
                destinationOffset = offset,
            )
            offset += SignModelContract.FEATURE_DIMENSION
        }
        return sequence
    }

    fun clear() {
        frames.clear()
    }

    companion object {
        const val DEFAULT_SEQUENCE_LENGTH = SignModelContract.SEQUENCE_LENGTH
        private const val DEFAULT_MINIMUM_HAND_FRAME_RATIO = 0.33f
        private const val MINIMUM_HAND_FRAMES = 1
    }
}
