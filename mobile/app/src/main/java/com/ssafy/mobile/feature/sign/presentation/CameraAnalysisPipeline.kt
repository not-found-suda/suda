package com.ssafy.mobile.feature.sign.presentation

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.Executor

data class YuvAnalysisFrame(
    val timestampNanos: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val planes: List<YuvPlane>,
)

/**
 * Plane buffers are valid only while the analyzer callback is running.
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
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
): ImageAnalysis {
    val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
    val previewUseCase = createPreviewUseCase(previewView, targetRotation)
    val analysisUseCase =
        createImageAnalysisUseCase(
            analyzerExecutor = analyzerExecutor,
            targetRotation = targetRotation,
            onFrameAvailable = onFrameAvailable,
        )
    val cameraSelector = selectCamera(cameraProvider)

    cameraProvider.unbindAll()
    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        cameraSelector,
        previewUseCase,
        analysisUseCase,
    )

    return analysisUseCase
}

private fun createPreviewUseCase(
    previewView: PreviewView,
    targetRotation: Int,
): Preview =
    Preview
        .Builder()
        .setTargetRotation(targetRotation)
        .build()
        .also { preview ->
            preview.surfaceProvider = previewView.surfaceProvider
        }

private fun createImageAnalysisUseCase(
    analyzerExecutor: Executor,
    targetRotation: Int,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
): ImageAnalysis =
    ImageAnalysis
        .Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setTargetRotation(targetRotation)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(analyzerExecutor, SignFrameAnalyzer(onFrameAvailable))
        }

private fun selectCamera(cameraProvider: ProcessCameraProvider): CameraSelector =
    when {
        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
            CameraSelector.DEFAULT_FRONT_CAMERA
        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
            CameraSelector.DEFAULT_BACK_CAMERA
        else -> error("No available camera")
    }

private class SignFrameAnalyzer(
    private val onFrameAvailable: (YuvAnalysisFrame) -> Unit,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            onFrameAvailable(image.toYuvAnalysisFrame())
        } finally {
            image.close()
        }
    }
}

private fun ImageProxy.toYuvAnalysisFrame(): YuvAnalysisFrame =
    YuvAnalysisFrame(
        timestampNanos = imageInfo.timestamp,
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
        planes =
            planes.map { plane ->
                YuvPlane(
                    buffer = plane.buffer.asReadOnlyBuffer(),
                    rowStride = plane.rowStride,
                    pixelStride = plane.pixelStride,
                )
            },
    )
