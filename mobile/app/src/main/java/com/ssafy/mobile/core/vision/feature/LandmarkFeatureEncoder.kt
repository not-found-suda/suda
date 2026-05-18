@file:Suppress("MagicNumber", "TooManyFunctions")

package com.ssafy.mobile.core.vision.feature

import com.ssafy.mobile.core.vision.inference.SignModelContract
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.LandmarkPoint
import kotlin.math.sqrt

class LandmarkFeatureEncoder(
    val isHandForwardFillEnabled: Boolean = true,
    private val normalizeMode: LandmarkFeatureNormalizeMode = LandmarkFeatureNormalizeMode.SHOULDER,
) {
    private var previousPose: List<LandmarkPoint>? = null
    private var previousLeftHand: List<LandmarkPoint>? = null
    private var previousRightHand: List<LandmarkPoint>? = null
    private var previousFace: List<LandmarkPoint>? = null

    fun encode(frame: LandmarkFrameResult): LandmarkFeatureFrame {
        val faceSelection = selectFaceLandmarks(frame.face.landmarks)
        val face = faceSelection.landmarks
        if (faceSelection.isFromCurrentFrame) {
            previousFace = face
        }
        val poseSelection = selectPoseLandmarks(frame.pose.landmarks)
        val pose = poseSelection.landmarks
        if (poseSelection.isFromCurrentFrame) {
            previousPose = pose
        }
        val rawLandmarks = createRawLandmarks(frame, face, pose)
        val distances =
            createHandFaceDistances(
                faceLandmarks = frame.face.landmarks,
                leftHand = frame.leftHand.landmarks,
                rightHand = frame.rightHand.landmarks,
            )
        val values =
            FloatArray(SignModelContract.FEATURE_DIMENSION).also { output ->
                writeFeatures(
                    output = output,
                    rawLandmarks = rawLandmarks,
                    distances = distances,
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
        previousFace = null
    }

    private fun selectFaceLandmarks(current: List<LandmarkPoint>): FaceSelection {
        val selectedFace =
            SignFeatureSpec.FACE_LANDMARK_INDICES.map { index ->
                current.getOrNull(index)
            }

        return if (selectedFace.all { landmark -> landmark != null }) {
            FaceSelection(
                landmarks = selectedFace.filterNotNull(),
                isFromCurrentFrame = true,
            )
        } else {
            FaceSelection(
                landmarks =
                    previousFace ?: List(SignFeatureSpec.SELECTED_FACE_LANDMARK_COUNT) {
                        ZERO_POINT
                    },
                isFromCurrentFrame = false,
            )
        }
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

    private fun createNormalizer(rawLandmarks: List<LandmarkPoint>): LandmarkNormalizer {
        val leftShoulder = rawLandmarks[SignFeatureSpec.LEFT_SHOULDER_LANDMARK_INDEX]
        val rightShoulder = rawLandmarks[SignFeatureSpec.RIGHT_SHOULDER_LANDMARK_INDEX]
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
            scale = scale,
        )
    }

    private fun createRawLandmarks(
        frame: LandmarkFrameResult,
        face: List<LandmarkPoint>,
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
            face +
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

    private fun createHandFaceDistances(
        faceLandmarks: List<LandmarkPoint>,
        leftHand: List<LandmarkPoint>,
        rightHand: List<LandmarkPoint>,
    ): FloatArray {
        if (!faceLandmarks.hasIndices(SignFeatureSpec.FACE_ANCHOR_INDICES)) {
            return FloatArray(SignFeatureSpec.HAND_FACE_DISTANCE_COUNT)
        }

        val faceAnchors = SignFeatureSpec.FACE_ANCHOR_INDICES.map { index -> faceLandmarks[index] }
        val values = FloatArray(SignFeatureSpec.HAND_FACE_DISTANCE_COUNT)
        var offset = 0
        offset =
            writeHandFaceDistances(
                output = values,
                offset = offset,
                handLandmarks = leftHand,
                faceAnchors = faceAnchors,
            )
        writeHandFaceDistances(
            output = values,
            offset = offset,
            handLandmarks = rightHand,
            faceAnchors = faceAnchors,
        )
        return values
    }

    private fun writeHandFaceDistances(
        output: FloatArray,
        offset: Int,
        handLandmarks: List<LandmarkPoint>,
        faceAnchors: List<LandmarkPoint>,
    ): Int {
        var currentOffset = offset
        val isEmptyHand = handLandmarks.isEmpty() || handLandmarks.first() == ZERO_POINT
        if (isEmptyHand || !handLandmarks.hasIndices(SignFeatureSpec.HAND_TIP_INDICES)) {
            return currentOffset + SignFeatureSpec.HAND_FACE_DISTANCE_PER_HAND
        }

        SignFeatureSpec.HAND_TIP_INDICES.forEach { tipIndex ->
            val tip = handLandmarks[tipIndex]
            faceAnchors.forEach { anchor ->
                output[currentOffset++] = tip.distanceTo(anchor)
            }
        }
        return currentOffset
    }

    private fun writeFeatures(
        output: FloatArray,
        rawLandmarks: List<LandmarkPoint>,
        distances: FloatArray,
    ) {
        val normalizer =
            when (normalizeMode) {
                LandmarkFeatureNormalizeMode.RAW -> null
                LandmarkFeatureNormalizeMode.SHOULDER -> createNormalizer(rawLandmarks)
            }
        var offset = 0
        rawLandmarks.forEach { landmark ->
            val encoded = normalizer?.normalize(landmark) ?: landmark
            output[offset++] = encoded.x
            output[offset++] = encoded.y
            output[offset++] = encoded.z
        }
        distances.forEach { distance ->
            output[offset++] =
                when (normalizer) {
                    null -> distance
                    else -> distance / normalizer.scale
                }
        }
    }

    private data class FaceSelection(
        val landmarks: List<LandmarkPoint>,
        val isFromCurrentFrame: Boolean,
    )

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

enum class LandmarkFeatureNormalizeMode {
    RAW,
    SHOULDER,
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
    const val SELECTED_FACE_LANDMARK_COUNT = 45
    const val SELECTED_POSE_LANDMARK_COUNT = 7
    const val COORDINATE_SIZE = 3
    const val HAND_FACE_DISTANCE_PER_HAND = 25
    const val HAND_FACE_DISTANCE_COUNT = 50
    const val NOSE_INDEX = 0
    const val LEFT_EYE_OUTER_INDEX = 7
    const val RIGHT_EYE_OUTER_INDEX = 8
    const val LEFT_SHOULDER_INDEX = 11
    const val RIGHT_SHOULDER_INDEX = 12
    const val LEFT_ELBOW_INDEX = 13
    const val RIGHT_ELBOW_INDEX = 14
    const val LEFT_HAND_OFFSET = 0
    const val RIGHT_HAND_OFFSET = LEFT_HAND_OFFSET + HAND_LANDMARK_COUNT * COORDINATE_SIZE
    const val FACE_OFFSET = RIGHT_HAND_OFFSET + HAND_LANDMARK_COUNT * COORDINATE_SIZE
    const val POSE_OFFSET = FACE_OFFSET + SELECTED_FACE_LANDMARK_COUNT * COORDINATE_SIZE
    const val DISTANCE_OFFSET = POSE_OFFSET + SELECTED_POSE_LANDMARK_COUNT * COORDINATE_SIZE
    const val LEFT_SHOULDER_LANDMARK_INDEX = 90
    const val RIGHT_SHOULDER_LANDMARK_INDEX = 91
    const val PROBE_LEFT_HAND_WRIST_INDEX = 0
    const val PROBE_RIGHT_HAND_WRIST_INDEX = 21
    const val PROBE_NOSE_INDEX = 72
    const val PROBE_LEFT_SHOULDER_INDEX = LEFT_SHOULDER_LANDMARK_INDEX
    const val PROBE_RIGHT_SHOULDER_INDEX = RIGHT_SHOULDER_LANDMARK_INDEX
    val BASE_FACE_LANDMARK_INDICES =
        listOf(
            70,
            63,
            105,
            66,
            336,
            296,
            334,
            293,
            33,
            160,
            158,
            133,
            153,
            144,
            362,
            385,
            387,
            263,
            373,
            380,
            61,
            146,
            91,
            181,
            84,
            17,
            314,
            405,
            321,
            375,
        )
    val FACEPLUS_LANDMARK_INDICES =
        listOf(
            1,
            4,
            152,
            234,
            454,
            172,
            397,
            148,
            377,
            13,
            14,
            78,
            308,
            82,
            312,
        )
    val FACE_LANDMARK_INDICES = BASE_FACE_LANDMARK_INDICES + FACEPLUS_LANDMARK_INDICES
    val FACE_ANCHOR_INDICES = listOf(1, 13, 152, 234, 454)
    val HAND_TIP_INDICES = listOf(4, 8, 12, 16, 20)
    val POSE_LANDMARK_INDICES =
        listOf(
            NOSE_INDEX,
            LEFT_EYE_OUTER_INDEX,
            RIGHT_EYE_OUTER_INDEX,
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

private fun List<LandmarkPoint>.hasIndices(indices: List<Int>): Boolean =
    indices.all { index -> index in this.indices }

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
