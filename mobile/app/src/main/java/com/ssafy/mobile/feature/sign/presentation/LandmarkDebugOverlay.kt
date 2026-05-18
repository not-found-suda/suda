@file:Suppress("MagicNumber")

package com.ssafy.mobile.feature.sign.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import com.ssafy.mobile.core.vision.landmark.HandLandmarks
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.LandmarkPoint

private const val LANDMARK_POINT_RADIUS = 5.5f
private const val LANDMARK_LINE_WIDTH = 3.0f

@Composable
internal fun LandmarkDebugOverlay(
    frame: LandmarkFrameResult?,
    analysisImageSize: IntSize,
    mirrorHorizontally: Boolean,
    modifier: Modifier = Modifier,
) {
    if (frame == null || analysisImageSize == IntSize.Zero) return

    Canvas(modifier = modifier) {
        val transform =
            LandmarkScreenTransform(
                canvasWidth = size.width,
                canvasHeight = size.height,
                imageWidth = analysisImageSize.width.toFloat(),
                imageHeight = analysisImageSize.height.toFloat(),
                mirrorHorizontally = mirrorHorizontally,
            )

        drawPose(frame.pose.landmarks, transform::toScreenOffset)
        drawHand(
            hand = frame.leftHand,
            pointColor = LEFT_HAND_POINT_COLOR,
            lineColor = LEFT_HAND_LINE_COLOR,
            toScreenOffset = transform::toScreenOffset,
        )
        drawHand(
            hand = frame.rightHand,
            pointColor = RIGHT_HAND_POINT_COLOR,
            lineColor = RIGHT_HAND_LINE_COLOR,
            toScreenOffset = transform::toScreenOffset,
        )
        drawLandmarkPoints(
            landmarks = frame.lips.landmarks,
            color = LIPS_POINT_COLOR,
            toScreenOffset = transform::toScreenOffset,
        )
    }
}

private class LandmarkScreenTransform(
    private val canvasWidth: Float,
    private val canvasHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    private val mirrorHorizontally: Boolean,
) {
    private val scale = maxOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
    private val drawnImageWidth = imageWidth * scale
    private val drawnImageHeight = imageHeight * scale
    private val imageOffset =
        Offset(
            x = (canvasWidth - drawnImageWidth) / 2.0f,
            y = (canvasHeight - drawnImageHeight) / 2.0f,
        )

    fun toScreenOffset(point: LandmarkPoint): Offset {
        val rawX = imageOffset.x + point.x * drawnImageWidth
        val screenX = if (mirrorHorizontally) canvasWidth - rawX else rawX
        return Offset(
            x = screenX,
            y = imageOffset.y + point.y * drawnImageHeight,
        )
    }
}

private fun DrawScope.drawPose(
    pose: List<LandmarkPoint>,
    toScreenOffset: (LandmarkPoint) -> Offset,
) {
    POSE_CONNECTIONS.forEach { (startIndex, endIndex) ->
        drawConnection(
            landmarks = pose,
            startIndex = startIndex,
            endIndex = endIndex,
            color = POSE_LINE_COLOR,
            toScreenOffset = toScreenOffset,
        )
    }
    POSE_LANDMARK_INDICES.forEach { index ->
        pose.getOrNull(index)?.let { point ->
            drawCircle(
                color = POSE_POINT_COLOR,
                radius = LANDMARK_POINT_RADIUS,
                center = toScreenOffset(point),
            )
        }
    }
}

private fun DrawScope.drawHand(
    hand: HandLandmarks,
    pointColor: Color,
    lineColor: Color,
    toScreenOffset: (LandmarkPoint) -> Offset,
) {
    HAND_CONNECTIONS.forEach { (startIndex, endIndex) ->
        drawConnection(
            landmarks = hand.landmarks,
            startIndex = startIndex,
            endIndex = endIndex,
            color = lineColor,
            toScreenOffset = toScreenOffset,
        )
    }
    drawLandmarkPoints(
        landmarks = hand.landmarks,
        color = pointColor,
        toScreenOffset = toScreenOffset,
    )
}

private fun DrawScope.drawLandmarkPoints(
    landmarks: List<LandmarkPoint>,
    color: Color,
    toScreenOffset: (LandmarkPoint) -> Offset,
) {
    landmarks.forEach { point ->
        drawCircle(
            color = color,
            radius = LANDMARK_POINT_RADIUS,
            center = toScreenOffset(point),
        )
    }
}

private fun DrawScope.drawConnection(
    landmarks: List<LandmarkPoint>,
    startIndex: Int,
    endIndex: Int,
    color: Color,
    toScreenOffset: (LandmarkPoint) -> Offset,
) {
    val start = landmarks.getOrNull(startIndex) ?: return
    val end = landmarks.getOrNull(endIndex) ?: return
    drawLine(
        color = color,
        start = toScreenOffset(start),
        end = toScreenOffset(end),
        strokeWidth = LANDMARK_LINE_WIDTH,
        cap = StrokeCap.Round,
    )
}

private val HAND_CONNECTIONS =
    listOf(
        0 to 1,
        1 to 2,
        2 to 3,
        3 to 4,
        0 to 5,
        5 to 6,
        6 to 7,
        7 to 8,
        5 to 9,
        9 to 10,
        10 to 11,
        11 to 12,
        9 to 13,
        13 to 14,
        14 to 15,
        15 to 16,
        13 to 17,
        0 to 17,
        17 to 18,
        18 to 19,
        19 to 20,
    )

private val POSE_CONNECTIONS =
    listOf(
        0 to 11,
        0 to 12,
        11 to 12,
        11 to 13,
        12 to 14,
    )

private val POSE_LANDMARK_INDICES = listOf(0, 11, 12, 13, 14)

private val LEFT_HAND_POINT_COLOR = Color(0xFF00E5FF)
private val LEFT_HAND_LINE_COLOR = Color(0xCC00E5FF)
private val RIGHT_HAND_POINT_COLOR = Color(0xFFFFD54F)
private val RIGHT_HAND_LINE_COLOR = Color(0xCCFFD54F)
private val POSE_POINT_COLOR = Color(0xFFFFFFFF)
private val POSE_LINE_COLOR = Color(0xCCFFFFFF)
private val LIPS_POINT_COLOR = Color(0xFFFF5252)
