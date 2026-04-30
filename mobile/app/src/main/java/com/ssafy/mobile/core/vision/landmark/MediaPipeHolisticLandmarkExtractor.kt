package com.ssafy.mobile.core.vision.landmark

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class MediaPipeHolisticLandmarkExtractor private constructor(
    private val landmarker: HolisticLandmarker?,
) : AutoCloseable {
    private val lastTimestampMs = AtomicLong(Long.MIN_VALUE)

    fun detect(
        bitmap: Bitmap,
        timestampMs: Long,
    ): LandmarkFrameResult {
        val activeLandmarker = landmarker ?: return LandmarkFrameResult.empty(timestampMs)
        val inputTimestampMs = nextTimestamp(timestampMs)
        val result =
            runCatching {
                activeLandmarker.detectForVideo(
                    BitmapImageBuilder(bitmap).build(),
                    inputTimestampMs,
                )
            }.getOrNull()

        return result?.toLandmarkFrameResult() ?: LandmarkFrameResult.empty(inputTimestampMs)
    }

    override fun close() {
        landmarker?.close()
    }

    private fun nextTimestamp(timestampMs: Long): Long {
        while (true) {
            val previous = lastTimestampMs.get()
            val next = timestampMs.coerceAtLeast(previous + 1)
            if (lastTimestampMs.compareAndSet(previous, next)) {
                return next
            }
        }
    }

    companion object {
        const val MODEL_ASSET_PATH = "models/holistic_landmarker.task"

        fun create(context: Context): MediaPipeHolisticLandmarkExtractor {
            val appContext = context.applicationContext
            val landmarker =
                if (appContext.hasAsset(MODEL_ASSET_PATH)) {
                    runCatching { createLandmarker(appContext) }
                        .onSuccess {
                            Log.d(TAG, "MediaPipe holistic landmarker loaded.")
                        }.onFailure { throwable ->
                            Log.e(TAG, "MediaPipe holistic landmarker load failed.", throwable)
                        }.getOrNull()
                } else {
                    Log.w(TAG, "MediaPipe holistic landmarker asset is missing.")
                    null
                }

            return MediaPipeHolisticLandmarkExtractor(landmarker)
        }

        private fun createLandmarker(context: Context): HolisticLandmarker {
            val baseOptions =
                BaseOptions
                    .builder()
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .build()
            val options =
                HolisticLandmarker.HolisticLandmarkerOptions
                    .builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.VIDEO)
                    .build()

            return HolisticLandmarker.createFromOptions(context, options)
        }

        private const val TAG = "SignPipeline"
    }
}

private fun Context.hasAsset(assetPath: String): Boolean =
    try {
        assets.open(assetPath).use { true }
    } catch (_: IOException) {
        false
    }

private fun HolisticLandmarkerResult.toLandmarkFrameResult(): LandmarkFrameResult =
    MediaPipeLandmarkMapper.map(
        MediaPipeLandmarkResult(
            timestampMs = timestampMs(),
            pose = poseLandmarks().toMediaPipePoints(),
            leftHand = leftHandLandmarks().toMediaPipePoints(),
            rightHand = rightHandLandmarks().toMediaPipePoints(),
            lips = faceLandmarks().toLipMediaPipePoints(),
        ),
    )

private fun List<NormalizedLandmark>.toMediaPipePoints(): List<MediaPipeLandmarkPoint> =
    map { landmark ->
        MediaPipeLandmarkPoint(
            x = landmark.x(),
            y = landmark.y(),
            z = landmark.z(),
        )
    }

private fun List<NormalizedLandmark>.toLipMediaPipePoints(): List<MediaPipeLandmarkPoint> =
    filterIndexed { index, _ -> index in LIP_LANDMARK_INDICES }
        .map { landmark ->
            MediaPipeLandmarkPoint(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
            )
        }

private val LIP_LANDMARK_INDICES: Set<Int> =
    LIP_LANDMARK_INDEX_CSV
        .split(',')
        .map { index -> index.trim().toInt() }
        .toSet()

private const val LIP_LANDMARK_INDEX_CSV =
    "61,146,91,181,84,17,314,405,321,375,291,185,40,39,37,0,267,269,270,409," +
        "78,95,88,178,87,14,317,402,318,324,308,191,80,81,82,13,312,311,310,415"
