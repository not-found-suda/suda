package com.ssafy.mobile.core.vision.landmark

data class LandmarkFrameResult(
    val timestampMs: Long,
    val pose: LandmarkGroup,
    val leftHand: HandLandmarks,
    val rightHand: HandLandmarks,
    val lips: LandmarkGroup,
) {
    val hasHands: Boolean = leftHand.hasLandmarks || rightHand.hasLandmarks
    val isEmpty: Boolean =
        !pose.hasLandmarks &&
            !leftHand.hasLandmarks &&
            !rightHand.hasLandmarks &&
            !lips.hasLandmarks

    companion object {
        fun empty(timestampMs: Long): LandmarkFrameResult =
            LandmarkFrameResult(
                timestampMs = timestampMs,
                pose = LandmarkGroup.empty(LandmarkGroupType.POSE),
                leftHand = HandLandmarks.empty(HandSide.LEFT),
                rightHand = HandLandmarks.empty(HandSide.RIGHT),
                lips = LandmarkGroup.empty(LandmarkGroupType.LIPS),
            )
    }
}

data class LandmarkGroup(
    val type: LandmarkGroupType,
    val landmarks: List<LandmarkPoint>,
) {
    val hasLandmarks: Boolean = landmarks.isNotEmpty()

    companion object {
        fun empty(type: LandmarkGroupType): LandmarkGroup =
            LandmarkGroup(
                type = type,
                landmarks = emptyList(),
            )
    }
}

data class HandLandmarks(
    val side: HandSide,
    val landmarks: List<LandmarkPoint>,
) {
    val hasLandmarks: Boolean = landmarks.isNotEmpty()

    companion object {
        fun empty(side: HandSide): HandLandmarks =
            HandLandmarks(
                side = side,
                landmarks = emptyList(),
            )
    }
}

enum class LandmarkGroupType {
    POSE,
    LIPS,
}

enum class HandSide {
    LEFT,
    RIGHT,
    UNKNOWN,
}

data class LandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)
