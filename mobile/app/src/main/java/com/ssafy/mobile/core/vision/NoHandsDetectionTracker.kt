package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult

class NoHandsDetectionTracker(
    private val detectionDelayMs: Long = DEFAULT_DETECTION_DELAY_MS,
) {
    private var noHandsStartedAtMs: Long? = null
    private var hasEmittedNoHands = false

    init {
        require(detectionDelayMs >= 0L) {
            "손 미검출 기준 시간은 0 이상이어야 합니다."
        }
    }

    fun onFrame(frame: LandmarkFrameResult): SignRecognitionEvent.NoHandsDetected? {
        if (frame.hasHands) {
            reset()
            return null
        }

        val startedAtMs = noHandsStartedAtMs ?: frame.timestampMs
        noHandsStartedAtMs = startedAtMs

        val elapsedMs = (frame.timestampMs - startedAtMs).coerceAtLeast(0L)
        return if (!hasEmittedNoHands && elapsedMs >= detectionDelayMs) {
            hasEmittedNoHands = true
            SignRecognitionEvent.NoHandsDetected
        } else {
            null
        }
    }

    fun reset() {
        noHandsStartedAtMs = null
        hasEmittedNoHands = false
    }

    private companion object {
        const val DEFAULT_DETECTION_DELAY_MS = 1_000L
    }
}
