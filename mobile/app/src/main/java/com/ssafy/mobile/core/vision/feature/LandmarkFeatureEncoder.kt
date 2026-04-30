package com.ssafy.mobile.core.vision.feature

import com.ssafy.mobile.core.vision.inference.SignModelContract
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.LandmarkPoint
import kotlin.math.sqrt

class LandmarkFeatureEncoder(
    val isHandForwardFillEnabled: Boolean = true,
) {
    private var previousPose: List<LandmarkPoint>? = null
    private var previousLeftHand: List<LandmarkPoint>? = null
    private var previousRightHand: List<LandmarkPoint>? = null

    fun encode(frame: LandmarkFrameResult): LandmarkFeatureFrame {
        val poseSelection = selectPoseLandmarks(frame.pose.landmarks)
        val pose = poseSelection.landmarks
        if (poseSelection.isFromCurrentFrame) {
            previousPose = pose
        }
        val normalizer = createNormalizer(pose)
        val rawLandmarks = createRawLandmarks(frame, pose)
        val values =
            FloatArray(SignModelContract.FEATURE_DIMENSION).also { output ->
                writeNormalizedLandmarks(
                    output = output,
                    rawLandmarks = rawLandmarks,
                    normalizer = normalizer,
                )
            }

        return LandmarkFeatureFrame(
            timestampMs = frame.timestampMs,
            values = values,
            hasHands = frame.hasHands,
            probe =
                LandmarkFeatureProbe(
                    raw = rawLandmarks.toProbePoints(),
                    normalized = values.toProbePoints(),
                ),
        )
    }

    fun reset() {
        previousPose = null
        previousLeftHand = null
        previousRightHand = null
    }

    private fun selectPoseLandmarks(current: List<LandmarkPoint>): PoseSelection {
        val selectedPose =
            SignFeatureSpec.POSE_LANDMARK_INDICES.map { index ->
                current.getOrNull(index)
            }

        return if (selectedPose.all { landmark -> landmark != null }) {
            PoseSelection(
                landmarks = selectedPose.filterNotNull(),
                isFromCurrentFrame = true,
            )
        } else {
            PoseSelection(
                landmarks =
                    previousPose ?: List(SignFeatureSpec.SELECTED_POSE_LANDMARK_COUNT) {
                        ZERO_POINT
                    },
                isFromCurrentFrame = false,
            )
        }
    }

    private fun createNormalizer(pose: List<LandmarkPoint>): LandmarkNormalizer {
        val leftShoulder = pose[SignFeatureSpec.SELECTED_LEFT_SHOULDER_INDEX]
        val rightShoulder = pose[SignFeatureSpec.SELECTED_RIGHT_SHOULDER_INDEX]
        val shoulderCenter =
            LandmarkPoint(
                x = (leftShoulder.x + rightShoulder.x) / 2f,
                y = (leftShoulder.y + rightShoulder.y) / 2f,
                z = (leftShoulder.z + rightShoulder.z) / 2f,
            )
        val shoulderWidth = leftShoulder.distanceTo(rightShoulder)
        val scale =
            when {
                shoulderWidth >= MIN_SHOULDER_WIDTH -> shoulderWidth
                else -> DEFAULT_SCALE
            }

        return LandmarkNormalizer(
            center = shoulderCenter,
            scale = scale ?: DEFAULT_SCALE,
        )
    }

    private fun createRawLandmarks(
        frame: LandmarkFrameResult,
        pose: List<LandmarkPoint>,
    ): List<LandmarkPoint> =
        createHandLandmarks(
            landmarks = frame.leftHand.landmarks,
            previous = previousLeftHand,
            updatePrevious = { previousLeftHand = it },
        ) +
            createHandLandmarks(
                landmarks = frame.rightHand.landmarks,
                previous = previousRightHand,
                updatePrevious = { previousRightHand = it },
            ) +
            pose

    private fun createHandLandmarks(
        landmarks: List<LandmarkPoint>,
        previous: List<LandmarkPoint>?,
        updatePrevious: (List<LandmarkPoint>) -> Unit,
    ): List<LandmarkPoint> {
        if (landmarks.isEmpty()) {
            return if (isHandForwardFillEnabled) {
                previous ?: createZeroHandLandmarks()
            } else {
                createZeroHandLandmarks()
            }
        }

        return List(SignFeatureSpec.HAND_LANDMARK_COUNT) { index ->
            landmarks.getOrNull(index) ?: ZERO_POINT
        }.also(updatePrevious)
    }

    private fun createZeroHandLandmarks(): List<LandmarkPoint> =
        List(SignFeatureSpec.HAND_LANDMARK_COUNT) { ZERO_POINT }

    private fun writeNormalizedLandmarks(
        output: FloatArray,
        rawLandmarks: List<LandmarkPoint>,
        normalizer: LandmarkNormalizer,
    ) {
        var offset = 0
        rawLandmarks.forEach { landmark ->
            val normalized = normalizer.normalize(landmark)
            output[offset++] = normalized.x
            output[offset++] = normalized.y
            output[offset++] = normalized.z
        }
    }

    private data class PoseSelection(
        val landmarks: List<LandmarkPoint>,
        val isFromCurrentFrame: Boolean,
    )

    private data class LandmarkNormalizer(
        val center: LandmarkPoint,
        val scale: Float,
    ) {
        fun normalize(point: LandmarkPoint): LandmarkPoint =
            LandmarkPoint(
                x = (point.x - center.x) / scale,
                y = (point.y - center.y) / scale,
                z = (point.z - center.z) / scale,
            )
    }

    private fun LandmarkPoint.distanceTo(other: LandmarkPoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private companion object {
        const val MIN_SHOULDER_WIDTH = 0.000001f
        const val DEFAULT_SCALE = 1f
        val ZERO_POINT = LandmarkPoint(0f, 0f, 0f)
    }
}

class LandmarkFeatureFrame(
    val timestampMs: Long,
    val values: FloatArray,
    val hasHands: Boolean = true,
    val probe: LandmarkFeatureProbe = LandmarkFeatureProbe.empty(),
) {
    init {
        require(values.size == SignModelContract.FEATURE_DIMENSION) {
            "Feature length must be ${SignModelContract.FEATURE_DIMENSION}."
        }
    }
}

data class LandmarkFeatureProbe(
    val raw: LandmarkFeatureProbePoints,
    val normalized: LandmarkFeatureProbePoints,
) {
    companion object {
        fun empty(): LandmarkFeatureProbe =
            LandmarkFeatureProbe(
                raw = LandmarkFeatureProbePoints.empty(),
                normalized = LandmarkFeatureProbePoints.empty(),
            )
    }
}

data class LandmarkFeatureProbePoints(
    val leftHandWrist: LandmarkPoint,
    val rightHandWrist: LandmarkPoint,
    val nose: LandmarkPoint,
    val leftShoulder: LandmarkPoint,
    val rightShoulder: LandmarkPoint,
) {
    companion object {
        fun empty(): LandmarkFeatureProbePoints =
            LandmarkFeatureProbePoints(
                leftHandWrist = EMPTY_POINT,
                rightHandWrist = EMPTY_POINT,
                nose = EMPTY_POINT,
                leftShoulder = EMPTY_POINT,
                rightShoulder = EMPTY_POINT,
            )

        private val EMPTY_POINT = LandmarkPoint(0f, 0f, 0f)
    }
}

object SignFeatureSpec {
    const val HAND_LANDMARK_COUNT = 21
    const val SELECTED_POSE_LANDMARK_COUNT = 5
    const val COORDINATE_SIZE = 3
    const val NOSE_INDEX = 0
    const val LEFT_SHOULDER_INDEX = 11
    const val RIGHT_SHOULDER_INDEX = 12
    const val LEFT_ELBOW_INDEX = 13
    const val RIGHT_ELBOW_INDEX = 14
    const val SELECTED_LEFT_SHOULDER_INDEX = 1
    const val SELECTED_RIGHT_SHOULDER_INDEX = 2
    const val LEFT_HAND_OFFSET = 0
    const val RIGHT_HAND_OFFSET = LEFT_HAND_OFFSET + HAND_LANDMARK_COUNT * COORDINATE_SIZE
    const val POSE_OFFSET = RIGHT_HAND_OFFSET + HAND_LANDMARK_COUNT * COORDINATE_SIZE
    const val PROBE_LEFT_HAND_WRIST_INDEX = 0
    const val PROBE_RIGHT_HAND_WRIST_INDEX = 21
    const val PROBE_NOSE_INDEX = 42
    const val PROBE_LEFT_SHOULDER_INDEX = 43
    const val PROBE_RIGHT_SHOULDER_INDEX = 44
    val POSE_LANDMARK_INDICES =
        listOf(
            NOSE_INDEX,
            LEFT_SHOULDER_INDEX,
            RIGHT_SHOULDER_INDEX,
            LEFT_ELBOW_INDEX,
            RIGHT_ELBOW_INDEX,
        )
}

private fun List<LandmarkPoint>.toProbePoints(): LandmarkFeatureProbePoints =
    LandmarkFeatureProbePoints(
        leftHandWrist = this[SignFeatureSpec.PROBE_LEFT_HAND_WRIST_INDEX],
        rightHandWrist = this[SignFeatureSpec.PROBE_RIGHT_HAND_WRIST_INDEX],
        nose = this[SignFeatureSpec.PROBE_NOSE_INDEX],
        leftShoulder = this[SignFeatureSpec.PROBE_LEFT_SHOULDER_INDEX],
        rightShoulder = this[SignFeatureSpec.PROBE_RIGHT_SHOULDER_INDEX],
    )

private fun FloatArray.toProbePoints(): LandmarkFeatureProbePoints =
    LandmarkFeatureProbePoints(
        leftHandWrist = toPoint(SignFeatureSpec.PROBE_LEFT_HAND_WRIST_INDEX),
        rightHandWrist = toPoint(SignFeatureSpec.PROBE_RIGHT_HAND_WRIST_INDEX),
        nose = toPoint(SignFeatureSpec.PROBE_NOSE_INDEX),
        leftShoulder = toPoint(SignFeatureSpec.PROBE_LEFT_SHOULDER_INDEX),
        rightShoulder = toPoint(SignFeatureSpec.PROBE_RIGHT_SHOULDER_INDEX),
    )

private fun FloatArray.toPoint(pointIndex: Int): LandmarkPoint {
    val offset = pointIndex * SignFeatureSpec.COORDINATE_SIZE
    return LandmarkPoint(
        x = this[offset],
        y = this[offset + 1],
        z = this[offset + 2],
    )
}
