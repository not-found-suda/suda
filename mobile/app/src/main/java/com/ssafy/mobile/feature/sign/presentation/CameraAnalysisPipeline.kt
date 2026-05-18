@file:Suppress("TooManyFunctions")

package com.ssafy.mobile.feature.sign.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executor

data class YuvAnalysisFrame(
    val timestampNanos: Long,
    val timestampMs: Long,
    val receivedAtElapsedMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val bitmap: Bitmap,
    val planes: List<YuvPlane>,
)

/**
 * Plane 버퍼는 분석 콜백이 실행되는 동안에만 유효한 읽기 전용 view입니다.
 * MediaPipe/TFLite 어댑터에서 mutable/direct 버퍼가 필요하면 별도로 복사해서 사용해야 합니다.
 */
data class YuvPlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

internal fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    analyzerExecutor: Executor,
    settings: CameraAnalysisSettings = CameraAnalysisSettings(),
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
): CameraBinding {
    val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_90
    val previewUseCase = createPreviewUseCase(previewView, targetRotation, settings)
    val cameraConfig = selectCamera(cameraProvider)
    val analysisUseCase =
        createImageAnalysisUseCase(
            analyzerExecutor = analyzerExecutor,
            targetRotation = targetRotation,
            settings = settings,
            onFrameAvailable = onFrameAvailable,
        )

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraConfig.selector,
        previewUseCase,
        analysisUseCase,
    )

    return CameraBinding(
        previewUseCase = previewUseCase,
        analysisUseCase = analysisUseCase,
    )
}

internal data class CameraBinding(
    val previewUseCase: UseCase,
    val analysisUseCase: ImageAnalysis,
)

private fun createPreviewUseCase(
    previewView: PreviewView,
    targetRotation: Int,
    settings: CameraAnalysisSettings,
): Preview =
    Preview
        .Builder()
        .setResolutionSelector(createLandscapeResolutionSelector(settings.targetResolution))
        .setTargetRotation(targetRotation)
        .build()
        .also { preview ->
            preview.surfaceProvider = previewView.surfaceProvider
        }

private fun createImageAnalysisUseCase(
    analyzerExecutor: Executor,
    targetRotation: Int,
    settings: CameraAnalysisSettings,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
): ImageAnalysis {
    val analysis =
        ImageAnalysis
            .Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(createLandscapeResolutionSelector(settings.targetResolution))
            .setTargetRotation(targetRotation)
            .build()

    analysis.setAnalyzer(
        analyzerExecutor,
        SignFrameAnalyzer(
            targetFps = settings.targetFps,
            analysisFrameInterval = settings.analysisFrameInterval,
            mirrorAnalysisInput = settings.mirrorAnalysisInput,
            onFrameAvailable = onFrameAvailable,
        ),
    )
    return analysis
}

private fun selectCamera(cameraProvider: ProcessCameraProvider): CameraAnalysisConfig =
    when {
        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
            CameraAnalysisConfig(
                selector = CameraSelector.DEFAULT_FRONT_CAMERA,
            )
        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
            CameraAnalysisConfig(
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
            )
        else -> error("No available camera")
    }

private data class CameraAnalysisConfig(
    val selector: CameraSelector,
)

private fun createLandscapeResolutionSelector(targetResolution: IntSize): ResolutionSelector =
    ResolutionSelector
        .Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(targetResolution.width, targetResolution.height),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            ),
        ).build()

private class SignFrameAnalyzer(
    private val targetFps: Int,
    private val analysisFrameInterval: Int,
    private val mirrorAnalysisInput: Boolean,
    private val onFrameAvailable: (YuvAnalysisFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    private var frameIndex = 0L
    private var lastAnalyzedAtMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            frameIndex += 1L
            val nowMs = SystemClock.elapsedRealtime()
            val targetIntervalMs = MILLIS_PER_SECOND / targetFps
            if (
                frameIndex % analysisFrameInterval != 0L ||
                nowMs - lastAnalyzedAtMs < targetIntervalMs
            ) {
                return
            }

            lastAnalyzedAtMs = nowMs
            onFrameAvailable(image.toYuvAnalysisFrame(mirrorAnalysisInput))
        } finally {
            image.close()
        }
    }
}

private fun ImageProxy.toYuvAnalysisFrame(mirrorAnalysisInput: Boolean): YuvAnalysisFrame =
    YuvAnalysisFrame(
        timestampNanos = imageInfo.timestamp,
        timestampMs = imageInfo.timestamp / NANOS_PER_MILLIS,
        receivedAtElapsedMs = SystemClock.elapsedRealtime(),
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
        bitmap = toUprightBitmap(mirrorAnalysisInput),
        planes =
            planes.map { plane ->
                YuvPlane(
                    buffer = plane.buffer.asReadOnlyBuffer(),
                    rowStride = plane.rowStride,
                    pixelStride = plane.pixelStride,
                )
            },
    )

private fun ImageProxy.toUprightBitmap(mirrorAnalysisInput: Boolean): Bitmap {
    val nv21 = toNv21()
    val jpegBytes =
        ByteArrayOutputStream().use { outputStream ->
            YuvImage(
                nv21,
                ImageFormat.NV21,
                width,
                height,
                null,
            ).compressToJpeg(
                Rect(0, 0, width, height),
                JPEG_QUALITY,
                outputStream,
            )
            outputStream.toByteArray()
        }
    val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    val rotated = bitmap.rotate(imageInfo.rotationDegrees)
    val mirrored =
        if (mirrorAnalysisInput) {
            rotated.mirrorHorizontally()
        } else {
            rotated
        }
    return mirrored.fitCenterToAspectRatio(LANDSCAPE_ASPECT_RATIO)
}

private fun ImageProxy.toNv21(): ByteArray {
    val ySize = width * height
    val chromaWidth = width / CHROMA_DIVISOR
    val chromaHeight = height / CHROMA_DIVISOR
    val nv21 = ByteArray(ySize + chromaWidth * chromaHeight * CHROMA_DIVISOR)

    planes[Y_PLANE_INDEX].copyTo(
        width = width,
        height = height,
        output = nv21,
        outputOffset = 0,
        outputPixelStride = 1,
    )
    planes[V_PLANE_INDEX].copyTo(
        width = chromaWidth,
        height = chromaHeight,
        output = nv21,
        outputOffset = ySize,
        outputPixelStride = CHROMA_DIVISOR,
    )
    planes[U_PLANE_INDEX].copyTo(
        width = chromaWidth,
        height = chromaHeight,
        output = nv21,
        outputOffset = ySize + 1,
        outputPixelStride = CHROMA_DIVISOR,
    )

    return nv21
}

private fun ImageProxy.PlaneProxy.copyTo(
    width: Int,
    height: Int,
    output: ByteArray,
    outputOffset: Int,
    outputPixelStride: Int,
) {
    val source = buffer.duplicate()
    var outputIndex = outputOffset

    repeat(height) { row ->
        val sourceRowOffset = row * rowStride
        repeat(width) { column ->
            output[outputIndex] = source.get(sourceRowOffset + column * pixelStride)
            outputIndex += outputPixelStride
        }
    }
}

private fun Bitmap.rotate(rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return this

    val rotated =
        Bitmap.createBitmap(
            this,
            0,
            0,
            width,
            height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) },
            true,
        )
    if (rotated != this) {
        recycle()
    }
    return rotated
}

private fun Bitmap.mirrorHorizontally(): Bitmap {
    val mirrored =
        Bitmap.createBitmap(
            this,
            0,
            0,
            width,
            height,
            Matrix().apply { postScale(-1f, 1f, width / 2f, height / 2f) },
            true,
        )
    if (mirrored != this) {
        recycle()
    }
    return mirrored
}

private fun Bitmap.fitCenterToAspectRatio(targetAspectRatio: Float): Bitmap {
    val outputWidth: Int
    val outputHeight: Int

    if (targetAspectRatio >= 1f) {
        outputWidth = width
        outputHeight = (width / targetAspectRatio).toInt().coerceAtLeast(1)
    } else {
        outputHeight = height
        outputWidth = (height * targetAspectRatio).toInt().coerceAtLeast(1)
    }

    val canvasBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBitmap)
    val srcRect = Rect(0, 0, width, height)
    val scale = minOf(outputWidth.toFloat() / width, outputHeight.toFloat() / height)
    val drawnWidth = width * scale
    val drawnHeight = height * scale
    val left = (outputWidth - drawnWidth) / 2f
    val top = (outputHeight - drawnHeight) / 2f
    val dstRect = RectF(left, top, left + drawnWidth, top + drawnHeight)
    canvas.drawBitmap(this, srcRect, dstRect, null)
    if (canvasBitmap != this) {
        recycle()
    }
    return canvasBitmap
}

private const val NANOS_PER_MILLIS = 1_000_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val JPEG_QUALITY = 90
private const val LANDSCAPE_ASPECT_RATIO = 16f / 9f
private const val CHROMA_DIVISOR = 2
private const val Y_PLANE_INDEX = 0
private const val U_PLANE_INDEX = 1
private const val V_PLANE_INDEX = 2
