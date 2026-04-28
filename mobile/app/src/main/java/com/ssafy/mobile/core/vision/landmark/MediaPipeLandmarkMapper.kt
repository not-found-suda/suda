package com.ssafy.mobile.core.vision.landmark

object MediaPipeLandmarkMapper {
    fun map(result: MediaPipeLandmarkResult): LandmarkFrameResult {
        if (result.isEmpty) {
            return LandmarkFrameResult.empty(timestampMs = result.timestampMs)
        }

        return LandmarkFrameResult(
            timestampMs = result.timestampMs,
            pose =
                LandmarkGroup(
                    type = LandmarkGroupType.POSE,
                    landmarks = result.pose.mapToLandmarkPoints(),
                ),
            leftHand =
                HandLandmarks(
                    side = HandSide.LEFT,
                    landmarks = result.leftHand.mapToLandmarkPoints(),
                ),
            rightHand =
                HandLandmarks(
                    side = HandSide.RIGHT,
                    landmarks = result.rightHand.mapToLandmarkPoints(),
                ),
            lips =
                LandmarkGroup(
                    type = LandmarkGroupType.LIPS,
                    landmarks = result.lips.mapToLandmarkPoints(),
                ),
        )
    }
}

data class MediaPipeLandmarkResult(
    val timestampMs: Long,
    val pose: List<MediaPipeLandmarkPoint> = emptyList(),
    val leftHand: List<MediaPipeLandmarkPoint> = emptyList(),
    val rightHand: List<MediaPipeLandmarkPoint> = emptyList(),
    val lips: List<MediaPipeLandmarkPoint> = emptyList(),
) {
    val isEmpty: Boolean =
        pose.isEmpty() &&
            leftHand.isEmpty() &&
            rightHand.isEmpty() &&
            lips.isEmpty()
}

data class MediaPipeLandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

private fun List<MediaPipeLandmarkPoint>.mapToLandmarkPoints(): List<LandmarkPoint> =
    map { landmark ->
        LandmarkPoint(
            x = landmark.x,
            y = landmark.y,
            z = landmark.z,
        )
    }
