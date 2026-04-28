package com.ssafy.mobile.core.vision.landmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipeLandmarkMapperTest {
    @Test
    fun mapsMediaPipeLandmarksToAppFrameModel() {
        val result =
            MediaPipeLandmarkMapper.map(
                MediaPipeLandmarkResult(
                    timestampMs = TIMESTAMP_MS,
                    pose =
                        listOf(
                            MediaPipeLandmarkPoint(
                                x = POSE_X,
                                y = POSE_Y,
                                z = POSE_Z,
                            ),
                        ),
                    leftHand =
                        listOf(
                            MediaPipeLandmarkPoint(
                                x = LEFT_HAND_X,
                                y = LEFT_HAND_Y,
                                z = LEFT_HAND_Z,
                            ),
                        ),
                    rightHand =
                        listOf(
                            MediaPipeLandmarkPoint(
                                x = RIGHT_HAND_X,
                                y = RIGHT_HAND_Y,
                                z = RIGHT_HAND_Z,
                            ),
                        ),
                    lips =
                        listOf(
                            MediaPipeLandmarkPoint(
                                x = LIPS_X,
                                y = LIPS_Y,
                                z = LIPS_Z,
                            ),
                        ),
                ),
            )

        assertEquals(TIMESTAMP_MS, result.timestampMs)
        assertTrue(result.hasHands)
        assertFalse(result.isEmpty)
        assertEquals(LandmarkGroupType.POSE, result.pose.type)
        assertEquals(HandSide.LEFT, result.leftHand.side)
        assertEquals(HandSide.RIGHT, result.rightHand.side)
        assertEquals(LandmarkGroupType.LIPS, result.lips.type)
        val poseLandmark = result.pose.landmarks.first()
        val leftHandLandmark = result.leftHand.landmarks.first()
        val rightHandLandmark = result.rightHand.landmarks.first()
        val lipLandmark = result.lips.landmarks.first()

        assertEquals(
            POSE_X,
            poseLandmark.x,
            FLOAT_DELTA,
        )
        assertEquals(
            LEFT_HAND_Y,
            leftHandLandmark.y,
            FLOAT_DELTA,
        )
        assertEquals(
            RIGHT_HAND_Z,
            rightHandLandmark.z,
            FLOAT_DELTA,
        )
        assertEquals(
            LIPS_X,
            lipLandmark.x,
            FLOAT_DELTA,
        )
    }

    @Test
    fun mapsEmptyMediaPipeResultToEmptyFrameModel() {
        val result =
            MediaPipeLandmarkMapper.map(
                MediaPipeLandmarkResult(timestampMs = TIMESTAMP_MS),
            )

        assertEquals(TIMESTAMP_MS, result.timestampMs)
        assertFalse(result.hasHands)
        assertTrue(result.isEmpty)
        assertTrue(result.pose.landmarks.isEmpty())
        assertTrue(result.leftHand.landmarks.isEmpty())
        assertTrue(result.rightHand.landmarks.isEmpty())
        assertTrue(result.lips.landmarks.isEmpty())
    }

    private companion object {
        const val TIMESTAMP_MS = 1_234L
        const val FLOAT_DELTA = 0.0001f
        const val POSE_X = 0.1f
        const val POSE_Y = 0.2f
        const val POSE_Z = 0.3f
        const val LEFT_HAND_X = 0.4f
        const val LEFT_HAND_Y = 0.5f
        const val LEFT_HAND_Z = 0.6f
        const val RIGHT_HAND_X = 0.7f
        const val RIGHT_HAND_Y = 0.8f
        const val RIGHT_HAND_Z = 0.9f
        const val LIPS_X = 0.11f
        const val LIPS_Y = 0.12f
        const val LIPS_Z = 0.13f
    }
}
