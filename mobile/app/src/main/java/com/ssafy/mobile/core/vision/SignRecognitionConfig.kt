package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.vision.feature.SignSequenceBuffer
import com.ssafy.mobile.core.vision.inference.SignModelContract

data class SignRecognitionConfig(
    val sequenceLength: Int = SignModelContract.SEQUENCE_LENGTH,
    val minimumHandFrameRatio: Float = SignSequenceBuffer.DEFAULT_MINIMUM_HAND_FRAME_RATIO,
    val confidenceThreshold: Float = SignModelContract.CONFIDENCE_THRESHOLD,
    val marginThreshold: Float = SignModelContract.MARGIN_THRESHOLD,
    val smoothingWindowSize: Int = SignPredictionStabilizer.DEFAULT_WINDOW_SIZE,
    val smoothingRequiredVotes: Int = SignPredictionStabilizer.DEFAULT_REQUIRED_VOTES,
    val emitCooldownMs: Long = SignPredictionStabilizer.DEFAULT_EMIT_COOLDOWN_MS,
    val noHandsDetectionDelayMs: Long = NoHandsDetectionTracker.DEFAULT_DETECTION_DELAY_MS,
) {
    init {
        require(sequenceLength == SignModelContract.SEQUENCE_LENGTH) {
            "Current TFLite model requires ${SignModelContract.SEQUENCE_LENGTH} frames."
        }
        require(minimumHandFrameRatio in MIN_HAND_FRAME_RATIO..MAX_HAND_FRAME_RATIO) {
            "Minimum hand frame ratio must be between $MIN_HAND_FRAME_RATIO and $MAX_HAND_FRAME_RATIO."
        }
        require(confidenceThreshold in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "Confidence threshold must be between $MIN_CONFIDENCE and $MAX_CONFIDENCE."
        }
        require(marginThreshold in MIN_CONFIDENCE..MAX_CONFIDENCE) {
            "Margin threshold must be between $MIN_CONFIDENCE and $MAX_CONFIDENCE."
        }
        require(smoothingWindowSize > 0) {
            "Smoothing window size must be greater than 0."
        }
        require(smoothingRequiredVotes in 1..smoothingWindowSize) {
            "Required votes must be between 1 and smoothing window size."
        }
        require(emitCooldownMs >= 0L) {
            "Emit cooldown must be 0 or greater."
        }
        require(noHandsDetectionDelayMs >= 0L) {
            "No-hands detection delay must be 0 or greater."
        }
    }

    companion object {
        private const val MIN_HAND_FRAME_RATIO = 0f
        private const val MAX_HAND_FRAME_RATIO = 1f
        private const val MIN_CONFIDENCE = 0f
        private const val MAX_CONFIDENCE = 1f
    }
}
