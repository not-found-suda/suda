package com.ssafy.mobile.feature.sign.presentation

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.MediaPipeHolisticLandmarkExtractor
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

private const val DEBUG_OVERLAY_ALPHA = 0.55f
private const val FPS_SAMPLE_INTERVAL_MILLIS = 1_000L

@Composable
fun SignRecognitionScreen(
    isSessionActive: Boolean,
    modifier: Modifier = Modifier,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit = {},
    onLandmarkFrameAvailable: (LandmarkFrameResult) -> Unit = {},
) {
    // 권한은 MainActivity에서 이미 허용되었으므로 즉시 카메라 프리뷰를 실행합니다.
    CameraPreviewContent(
        isSessionActive = isSessionActive,
        onFrameAvailable = onFrameAvailable,
        onLandmarkFrameAvailable = onLandmarkFrameAvailable,
        modifier = modifier,
    )
}

@Composable
private fun CameraPreviewContent(
    isSessionActive: Boolean,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
    onLandmarkFrameAvailable: (LandmarkFrameResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrameAvailable by rememberUpdatedState(onFrameAvailable)
    val currentOnLandmarkFrameAvailable by rememberUpdatedState(onLandmarkFrameAvailable)
    val appContext = remember(context) { context.applicationContext }
    val landmarkExtractor =
        remember(appContext) {
            MediaPipeHolisticLandmarkExtractor.create(appContext)
        }
    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(appContext) }
    val analyzedFrameCount = remember { AtomicLong(0L) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }
    var frameCount by remember { mutableStateOf(0L) }
    var fps by remember { mutableStateOf(0.0) }

    DisposableEffect(landmarkExtractor) {
        onDispose { landmarkExtractor.close() }
    }

    LaunchedEffect(analyzedFrameCount, isSessionActive) {
        if (!isSessionActive) {
            frameCount = 0L
            fps = 0.0
            return@LaunchedEffect
        }

        var previousFrameCount = analyzedFrameCount.get()
        var previousSampleTimeMillis = SystemClock.elapsedRealtime()

        while (true) {
            delay(FPS_SAMPLE_INTERVAL_MILLIS)
            val currentFrameCount = analyzedFrameCount.get()
            val currentSampleTimeMillis = SystemClock.elapsedRealtime()
            val elapsedMillis = currentSampleTimeMillis - previousSampleTimeMillis
            val framesSincePrevious = currentFrameCount - previousFrameCount
            val elapsedSeconds =
                elapsedMillis.coerceAtLeast(1L) /
                    FPS_SAMPLE_INTERVAL_MILLIS.toDouble()

            frameCount = currentFrameCount
            fps = framesSincePrevious / elapsedSeconds

            previousFrameCount = currentFrameCount
            previousSampleTimeMillis = currentSampleTimeMillis
        }
    }

    if (isSessionActive) {
        ActiveCameraBinding(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            cameraProviderFuture = cameraProviderFuture,
            orientation = configuration.orientation,
            onFrameAvailable = { frame ->
                analyzedFrameCount.incrementAndGet()
                val landmarkFrame =
                    landmarkExtractor.detect(
                        bitmap = frame.bitmap,
                        timestampMs = frame.timestampMs,
                    )
                currentOnLandmarkFrameAvailable(landmarkFrame)
                currentOnFrameAvailable(frame)
            },
            onCameraErrorChanged = { message -> cameraErrorMessage = message },
        )
    }

    CameraPreviewBox(
        previewView = previewView,
        cameraErrorMessage = cameraErrorMessage,
        frameCount = frameCount,
        fps = fps,
        modifier = modifier,
    )
}

@Composable
private fun ActiveCameraBinding(
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    orientation: Int,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
    onCameraErrorChanged: (String?) -> Unit,
) {
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(analyzerExecutor) {
        onDispose { analyzerExecutor.shutdown() }
    }

    CameraBindingEffect(
        lifecycleOwner = lifecycleOwner,
        previewView = previewView,
        cameraProviderFuture = cameraProviderFuture,
        analyzerExecutor = analyzerExecutor,
        orientation = orientation,
        onFrameAvailable = onFrameAvailable,
        onCameraErrorChanged = onCameraErrorChanged,
    )
}

@Composable
private fun CameraBindingEffect(
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    analyzerExecutor: Executor,
    orientation: Int,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
    onCameraErrorChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(
        lifecycleOwner,
        previewView,
        cameraProviderFuture,
        analyzerExecutor,
        orientation,
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        var boundAnalysisUseCase: ImageAnalysis? = null
        val listener =
            Runnable {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases(
                        cameraProvider = cameraProvider,
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        analyzerExecutor = analyzerExecutor,
                        onFrameAvailable = onFrameAvailable,
                    )
                }.onSuccess { analysisUseCase ->
                    boundAnalysisUseCase = analysisUseCase
                    onCameraErrorChanged(null)
                }.onFailure {
                    onCameraErrorChanged("Could not start the camera. Please restart the app.")
                }
            }

        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            boundAnalysisUseCase?.clearAnalyzer()
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }
}

@Composable
private fun CameraPreviewBox(
    previewView: PreviewView,
    cameraErrorMessage: String?,
    frameCount: Long,
    fps: Double,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        FrameAnalysisDebugOverlay(
            frameCount = frameCount,
            fps = fps,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
        )

        cameraErrorMessage?.let { message ->
            Text(
                text = message,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun FrameAnalysisDebugOverlay(
    frameCount: Long,
    fps: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.scrim.copy(alpha = DEBUG_OVERLAY_ALPHA),
    ) {
        Text(
            text = "Frames: $frameCount\nFPS: ${String.format(Locale.US, "%.1f", fps)}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
