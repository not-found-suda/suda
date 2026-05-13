@file:Suppress("LongMethod", "LongParameterList")

package com.ssafy.mobile.feature.sign.presentation

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

private const val DEBUG_OVERLAY_BACKGROUND_ALPHA = 0.72f
private const val FPS_SAMPLE_INTERVAL_MILLIS = 1_000L
private const val DEBUG_OVERLAY_UPDATE_INTERVAL_MILLIS = 100L
private const val FRONT_CAMERA_PREVIEW_IS_MIRRORED = true
private val DEBUG_OVERLAY_BACKGROUND = Color.Black.copy(alpha = DEBUG_OVERLAY_BACKGROUND_ALPHA)
private val DEBUG_OVERLAY_TEXT_COLOR = Color.White

@Composable
fun SignRecognitionScreen(
    isSessionActive: Boolean,
    modifier: Modifier = Modifier,
    cameraAnalysisSettings: CameraAnalysisSettings = CameraAnalysisSettings(),
    showDebugOverlay: Boolean = false,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit = {},
    onLandmarkFrameAvailable: (LandmarkFrameResult) -> Unit = {},
    onCameraMetricsChanged: (CameraPerformanceMetrics) -> Unit = {},
) {
    // 권한은 MainActivity에서 이미 허용되었으므로 즉시 카메라 프리뷰를 실행합니다.
    CameraPreviewContent(
        isSessionActive = isSessionActive,
        cameraAnalysisSettings = cameraAnalysisSettings,
        showDebugOverlay = showDebugOverlay,
        onFrameAvailable = onFrameAvailable,
        onLandmarkFrameAvailable = onLandmarkFrameAvailable,
        onCameraMetricsChanged = onCameraMetricsChanged,
        modifier = modifier,
    )
}

@Composable
private fun CameraPreviewContent(
    isSessionActive: Boolean,
    cameraAnalysisSettings: CameraAnalysisSettings,
    showDebugOverlay: Boolean,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
    onLandmarkFrameAvailable: (LandmarkFrameResult) -> Unit,
    onCameraMetricsChanged: (CameraPerformanceMetrics) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrameAvailable by rememberUpdatedState(onFrameAvailable)
    val currentOnLandmarkFrameAvailable by rememberUpdatedState(onLandmarkFrameAvailable)
    val currentOnCameraMetricsChanged by rememberUpdatedState(onCameraMetricsChanged)
    val appContext = remember(context) { context.applicationContext }
    val landmarkExtractor =
        remember(appContext) {
            MediaPipeHolisticLandmarkExtractor.create(appContext)
        }
    val previewView =
        remember {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(appContext) }
    val analyzedFrameCount = remember { AtomicLong(0L) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }
    var frameCount by remember { mutableStateOf(0L) }
    var fps by remember { mutableStateOf(0.0) }
    var latestLandmarkFrame by remember { mutableStateOf<LandmarkFrameResult?>(null) }
    var latestAnalysisImageSize by remember { mutableStateOf(IntSize.Zero) }
    var latestAnalysisBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val lastDebugOverlayUpdateAtMs = remember { AtomicLong(0L) }

    DisposableEffect(landmarkExtractor) {
        onDispose { landmarkExtractor.close() }
    }

    LaunchedEffect(isSessionActive) {
        if (!isSessionActive) {
            latestLandmarkFrame = null
            latestAnalysisImageSize = IntSize.Zero
            latestAnalysisBitmap = null
        }
    }

    FrameAnalysisStatsEffect(
        analyzedFrameCount = analyzedFrameCount,
        isSessionActive = isSessionActive,
        onStatsChanged = { currentFrameCount, currentFps ->
            frameCount = currentFrameCount
            fps = currentFps
            currentOnCameraMetricsChanged(
                CameraPerformanceMetrics(
                    frameCount = currentFrameCount,
                    cameraFps = currentFps,
                ),
            )
        },
    )

    if (isSessionActive) {
        ActiveCameraBinding(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            cameraProviderFuture = cameraProviderFuture,
            orientation = configuration.orientation,
            cameraAnalysisSettings = cameraAnalysisSettings,
            onFrameAvailable = { frame ->
                analyzedFrameCount.incrementAndGet()
                val pipelineStartedAtMs = SystemClock.elapsedRealtime()
                val mediaPipeStartedAtNanos = SystemClock.elapsedRealtimeNanos()
                val landmarkFrame =
                    landmarkExtractor.detect(
                        bitmap = frame.bitmap,
                        timestampMs = frame.timestampMs,
                    )
                val mediaPipeMs =
                    (SystemClock.elapsedRealtimeNanos() - mediaPipeStartedAtNanos) /
                        NANOS_PER_MILLIS
                if (showDebugOverlay) {
                    val nowMs = SystemClock.elapsedRealtime()
                    val lastOverlayUpdateAtMs = lastDebugOverlayUpdateAtMs.get()
                    if (
                        nowMs - lastOverlayUpdateAtMs >=
                        DEBUG_OVERLAY_UPDATE_INTERVAL_MILLIS
                    ) {
                        lastDebugOverlayUpdateAtMs.set(nowMs)
                        previewView.post {
                            latestLandmarkFrame = landmarkFrame
                            latestAnalysisBitmap = frame.bitmap
                            latestAnalysisImageSize =
                                IntSize(frame.bitmap.width, frame.bitmap.height)
                        }
                    }
                }
                currentOnLandmarkFrameAvailable(landmarkFrame)
                currentOnFrameAvailable(frame)
                currentOnCameraMetricsChanged(
                    CameraPerformanceMetrics(
                        mediaPipeMs = mediaPipeMs,
                        pipelineLatencyMs =
                            (SystemClock.elapsedRealtime() - pipelineStartedAtMs).toDouble(),
                        analysisImageSize = IntSize(frame.bitmap.width, frame.bitmap.height),
                    ),
                )
            },
            onCameraErrorChanged = { message -> cameraErrorMessage = message },
        )
    }

    CameraPreviewBox(
        previewView = previewView,
        cameraErrorMessage = cameraErrorMessage,
        frameCount = frameCount,
        fps = fps,
        landmarkFrame = latestLandmarkFrame,
        analysisImageSize = latestAnalysisImageSize,
        analysisBitmap = latestAnalysisBitmap,
        showDebugOverlay = showDebugOverlay,
        modifier = modifier,
    )
}

@Composable
private fun FrameAnalysisStatsEffect(
    analyzedFrameCount: AtomicLong,
    isSessionActive: Boolean,
    onStatsChanged: (Long, Double) -> Unit,
) {
    LaunchedEffect(analyzedFrameCount, isSessionActive) {
        if (!isSessionActive) {
            onStatsChanged(0L, 0.0)
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

            onStatsChanged(currentFrameCount, framesSincePrevious / elapsedSeconds)

            previousFrameCount = currentFrameCount
            previousSampleTimeMillis = currentSampleTimeMillis
        }
    }
}

@Composable
private fun ActiveCameraBinding(
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    orientation: Int,
    cameraAnalysisSettings: CameraAnalysisSettings,
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
        cameraAnalysisSettings = cameraAnalysisSettings,
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
    cameraAnalysisSettings: CameraAnalysisSettings,
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
        cameraAnalysisSettings,
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val isDisposed = AtomicBoolean(false)
        var cameraBinding: CameraBinding? = null
        val listener =
            Runnable {
                if (isDisposed.get()) {
                    return@Runnable
                }

                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    if (isDisposed.get()) {
                        return@Runnable
                    }
                    bindCameraUseCases(
                        cameraProvider = cameraProvider,
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        analyzerExecutor = analyzerExecutor,
                        settings = cameraAnalysisSettings,
                        onFrameAvailable = onFrameAvailable,
                    )
                }.onSuccess { binding ->
                    if (isDisposed.get()) {
                        binding.analysisUseCase.clearAnalyzer()
                        runCatching {
                            cameraProviderFuture.get().unbind(
                                binding.previewUseCase,
                                binding.analysisUseCase,
                            )
                        }
                        return@onSuccess
                    }
                    cameraBinding = binding
                    onCameraErrorChanged(null)
                }.onFailure {
                    if (!isDisposed.get()) {
                        onCameraErrorChanged("Could not start the camera. Please restart the app.")
                    }
                }
            }

        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            isDisposed.set(true)
            cameraBinding?.analysisUseCase?.clearAnalyzer()
            if (cameraProviderFuture.isDone) {
                cameraBinding?.let { binding ->
                    runCatching {
                        cameraProviderFuture.get().unbind(
                            binding.previewUseCase,
                            binding.analysisUseCase,
                        )
                    }
                }
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
    landmarkFrame: LandmarkFrameResult?,
    analysisImageSize: IntSize,
    analysisBitmap: Bitmap?,
    showDebugOverlay: Boolean,
    modifier: Modifier = Modifier,
) {
    val showAnalysisBitmap = showDebugOverlay && analysisBitmap != null

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(if (showAnalysisBitmap) 0.0f else 1.0f),
        )

        if (showAnalysisBitmap) {
            Image(
                bitmap = analysisBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showDebugOverlay) {
            LandmarkDebugOverlay(
                frame = landmarkFrame,
                analysisImageSize = analysisImageSize,
                mirrorHorizontally = analysisBitmap == null && FRONT_CAMERA_PREVIEW_IS_MIRRORED,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showDebugOverlay) {
            FrameAnalysisDebugOverlay(
                frameCount = frameCount,
                fps = fps,
                landmarkFrame = landmarkFrame,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
            )
        }

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

private const val NANOS_PER_MILLIS = 1_000_000.0

@Composable
private fun FrameAnalysisDebugOverlay(
    frameCount: Long,
    fps: Double,
    landmarkFrame: LandmarkFrameResult?,
    modifier: Modifier = Modifier,
) {
    val landmarkSummary =
        landmarkFrame
            ?.let { frame ->
                "\nPose: ${frame.pose.landmarks.size}" +
                    "  L: ${frame.leftHand.landmarks.size}" +
                    "  R: ${frame.rightHand.landmarks.size}" +
                    "  Lips: ${frame.lips.landmarks.size}"
            }.orEmpty()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = DEBUG_OVERLAY_BACKGROUND,
    ) {
        Text(
            text =
                "Frames: $frameCount\n" +
                    "FPS: ${String.format(Locale.US, "%.1f", fps)}" +
                    landmarkSummary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = DEBUG_OVERLAY_TEXT_COLOR,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
