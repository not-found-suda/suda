package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.landmark.HandLandmarks
import com.ssafy.mobile.core.vision.landmark.HandSide
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.LandmarkGroup
import com.ssafy.mobile.core.vision.landmark.LandmarkGroupType
import com.ssafy.mobile.core.vision.landmark.LandmarkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoHandsDetectionTrackerTest {
    @Test
    fun doesNotEmitBeforeDetectionDelay() {
        val tracker = NoHandsDetectionTracker(detectionDelayMs = DETECTION_DELAY_MS)

        assertNull(tracker.onFrame(createNoHandsFrame(timestampMs = 0L)))
        assertNull(tracker.onFrame(createNoHandsFrame(timestampMs = DETECTION_DELAY_MS - 1L)))
    }

    @Test
    fun emitsNoHandsDetectedAfterDetectionDelay() {
        val tracker = NoHandsDetectionTracker(detectionDelayMs = DETECTION_DELAY_MS)

        tracker.onFrame(createNoHandsFrame(timestampMs = 0L))

        assertEquals(
            SignRecognitionEvent.NoHandsDetected,
            tracker.onFrame(createNoHandsFrame(timestampMs = DETECTION_DELAY_MS)),
        )
    }

    @Test
    fun doesNotEmitRepeatedNoHandsEventsUntilHandsReturn() {
        val tracker = NoHandsDetectionTracker(detectionDelayMs = DETECTION_DELAY_MS)

        tracker.onFrame(createNoHandsFrame(timestampMs = 0L))
        tracker.onFrame(createNoHandsFrame(timestampMs = DETECTION_DELAY_MS))

        assertNull(tracker.onFrame(createNoHandsFrame(timestampMs = DETECTION_DELAY_MS * 2L)))
    }

    @Test
    fun resetsNoHandsStateWhenHandsReturn() {
        val tracker = NoHandsDetectionTracker(detectionDelayMs = DETECTION_DELAY_MS)

        tracker.onFrame(createNoHandsFrame(timestampMs = 0L))
        tracker.onFrame(createNoHandsFrame(timestampMs = DETECTION_DELAY_MS))
        assertNull(tracker.onFrame(createHandsFrame(timestampMs = DETECTION_DELAY_MS + 1L)))
        assertNull(tracker.onFrame(createNoHandsFrame(timestampMs = DETECTION_DELAY_MS + 2L)))

        assertEquals(
            SignRecognitionEvent.NoHandsDetected,
            tracker.onFrame(createNoHandsFrame(timestampMs = DETECTION_DELAY_MS * 2L + 2L)),
        )
    }

    private fun createNoHandsFrame(timestampMs: Long): LandmarkFrameResult =
        LandmarkFrameResult(
            timestampMs = timestampMs,
            pose = LandmarkGroup.empty(LandmarkGroupType.POSE),
            leftHand = HandLandmarks.empty(HandSide.LEFT),
            rightHand = HandLandmarks.empty(HandSide.RIGHT),
            lips = LandmarkGroup.empty(LandmarkGroupType.LIPS),
        )

    private fun createHandsFrame(timestampMs: Long): LandmarkFrameResult =
        LandmarkFrameResult(
            timestampMs = timestampMs,
            pose = LandmarkGroup.empty(LandmarkGroupType.POSE),
            leftHand =
                HandLandmarks(
                    side = HandSide.LEFT,
                    landmarks =
                        listOf(
                            LandmarkPoint(
                                x = 0f,
                                y = 0f,
                                z = 0f,
                            ),
                        ),
                ),
            rightHand = HandLandmarks.empty(HandSide.RIGHT),
            lips = LandmarkGroup.empty(LandmarkGroupType.LIPS),
        )

    private companion object {
        const val DETECTION_DELAY_MS = 1_000L
    }
}
