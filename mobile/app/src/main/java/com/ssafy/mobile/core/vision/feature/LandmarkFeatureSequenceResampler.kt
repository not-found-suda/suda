@file:Suppress("ReturnCount")

package com.ssafy.mobile.core.vision.feature

import com.ssafy.mobile.core.vision.inference.SignModelContract

object LandmarkFeatureSequenceResampler {
    fun resampleToModelInput(
        frames: List<LandmarkFeatureFrame>,
        sequenceLength: Int = SignModelContract.SEQUENCE_LENGTH,
    ): FloatArray {
        require(sequenceLength > 0) {
            "Sequence length must be positive."
        }

        val output =
            FloatArray(sequenceLength * SignModelContract.FEATURE_DIMENSION)
        if (frames.isEmpty()) {
            return output
        }

        if (frames.size == sequenceLength) {
            frames.copyIntoOutput(output)
            return output
        }

        if (frames.size > sequenceLength) {
            sampleEvenly(
                frames = frames,
                output = output,
                sequenceLength = sequenceLength,
            )
            return output
        }

        if (frames.size < MIN_INTERPOLATION_FRAME_COUNT) {
            interpolateToLength(
                frames = frames,
                output = output,
                sequenceLength = sequenceLength,
            )
            return output
        }

        writeWithLastFramePadding(
            frames = frames,
            output = output,
            sequenceLength = sequenceLength,
        )
        return output
    }

    private fun List<LandmarkFeatureFrame>.copyIntoOutput(output: FloatArray) {
        forEachIndexed { index, frame ->
            frame.values.copyInto(
                destination = output,
                destinationOffset = index * SignModelContract.FEATURE_DIMENSION,
            )
        }
    }

    private fun sampleEvenly(
        frames: List<LandmarkFeatureFrame>,
        output: FloatArray,
        sequenceLength: Int,
    ) {
        val sourceMaxIndex = frames.lastIndex.toFloat()
        val targetMaxIndex = (sequenceLength - 1).coerceAtLeast(1).toFloat()
        for (targetIndex in 0 until sequenceLength) {
            val sourceIndex = (targetIndex * sourceMaxIndex / targetMaxIndex).toInt()
            frames[sourceIndex].values.copyInto(
                destination = output,
                destinationOffset = targetIndex * SignModelContract.FEATURE_DIMENSION,
            )
        }
    }

    private fun interpolateToLength(
        frames: List<LandmarkFeatureFrame>,
        output: FloatArray,
        sequenceLength: Int,
    ) {
        val sourceMaxIndex = frames.lastIndex.toFloat()
        val targetMaxIndex = (sequenceLength - 1).coerceAtLeast(1).toFloat()
        for (targetIndex in 0 until sequenceLength) {
            val sourcePosition = targetIndex * sourceMaxIndex / targetMaxIndex
            val lowerIndex = sourcePosition.toInt().coerceAtMost(frames.lastIndex)
            val upperIndex = (lowerIndex + 1).coerceAtMost(frames.lastIndex)
            val weight = sourcePosition - lowerIndex
            writeInterpolatedFrame(
                output = output,
                outputOffset = targetIndex * SignModelContract.FEATURE_DIMENSION,
                lower = frames[lowerIndex].values,
                upper = frames[upperIndex].values,
                weight = weight,
            )
        }
    }

    private fun writeWithLastFramePadding(
        frames: List<LandmarkFeatureFrame>,
        output: FloatArray,
        sequenceLength: Int,
    ) {
        frames.copyIntoOutput(output)
        val lastFrame = frames.last()
        for (targetIndex in frames.size until sequenceLength) {
            lastFrame.values.copyInto(
                destination = output,
                destinationOffset = targetIndex * SignModelContract.FEATURE_DIMENSION,
            )
        }
    }

    private fun writeInterpolatedFrame(
        output: FloatArray,
        outputOffset: Int,
        lower: FloatArray,
        upper: FloatArray,
        weight: Float,
    ) {
        for (featureIndex in 0 until SignModelContract.FEATURE_DIMENSION) {
            output[outputOffset + featureIndex] =
                lower[featureIndex] +
                (upper[featureIndex] - lower[featureIndex]) * weight
        }
    }

    private const val MIN_INTERPOLATION_FRAME_COUNT = 15
}
