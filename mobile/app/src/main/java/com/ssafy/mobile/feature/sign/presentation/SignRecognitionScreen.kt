package com.ssafy.mobile.feature.sign.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.ssafy.mobile.core.permission.PermissionGuide
import com.ssafy.mobile.core.permission.PermissionRequestState
import com.ssafy.mobile.core.ui.components.AppPrimaryButton
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

private const val DEBUG_OVERLAY_ALPHA = 0.55f
private const val FPS_SAMPLE_INTERVAL_MILLIS = 1_000L

@Composable
fun SignRecognitionScreen(
    modifier: Modifier = Modifier,
    onFrameAvailable: (YuvAnalysisFrame) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    var hasRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var permissionRefreshKey by remember { mutableIntStateOf(0) }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            hasRequestedPermission = true
            permissionRefreshKey += 1
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionRefreshKey += 1
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionState =
        remember(context, activity, hasRequestedPermission, permissionRefreshKey) {
            resolvePermissionState(
                context = context,
                activity = activity,
                hasRequestedPermission = hasRequestedPermission,
            )
        }

    when (permissionState) {
        PermissionRequestState.Granted ->
            CameraPreviewContent(
                onFrameAvailable = onFrameAvailable,
                modifier = modifier,
            )
        PermissionRequestState.PermanentlyDenied ->
            PermissionGuide(
                title = "Camera permission is required",
                description = "Allow camera permission in Settings to start sign recognition.",
                onOpenSettings = { context.openAppSettings() },
                modifier = modifier,
            )
        PermissionRequestState.ShouldRequest,
        PermissionRequestState.Denied,
        PermissionRequestState.Idle,
        -> {
            RequestPermissionContent(
                onRequestPermission = {
                    hasRequestedPermission = true
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun RequestPermissionContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Allow camera permission to start sign recognition.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        AppPrimaryButton(
            text = "Allow camera",
            onClick = onRequestPermission,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun CameraPreviewContent(
    onFrameAvailable: (YuvAnalysisFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrameAvailable by rememberUpdatedState(onFrameAvailable)
    val appContext = remember(context) { context.applicationContext }
    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(appContext) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzedFrameCount = remember { AtomicLong(0L) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }
    var frameCount by remember { mutableStateOf(0L) }
    var fps by remember { mutableStateOf(0.0) }

    LaunchedEffect(analyzedFrameCount) {
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

    DisposableEffect(analyzerExecutor) {
        onDispose { analyzerExecutor.shutdown() }
    }

    CameraBindingEffect(
        lifecycleOwner = lifecycleOwner,
        previewView = previewView,
        cameraProviderFuture = cameraProviderFuture,
        analyzerExecutor = analyzerExecutor,
        orientation = configuration.orientation,
        onFrameAvailable = { frame ->
            analyzedFrameCount.incrementAndGet()
            currentOnFrameAvailable(frame)
        },
        onCameraErrorChanged = { message -> cameraErrorMessage = message },
    )

    CameraPreviewBox(
        previewView = previewView,
        cameraErrorMessage = cameraErrorMessage,
        frameCount = frameCount,
        fps = fps,
        modifier = modifier,
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
            runCatching { cameraProviderFuture.get().unbindAll() }
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

private fun resolvePermissionState(
    context: Context,
    activity: Activity?,
    hasRequestedPermission: Boolean,
): PermissionRequestState {
    val isGranted =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    return when {
        isGranted -> PermissionRequestState.Granted
        !hasRequestedPermission -> PermissionRequestState.ShouldRequest
        activity == null -> PermissionRequestState.Denied
        shouldShowCameraRationale(activity) -> PermissionRequestState.Denied
        else -> PermissionRequestState.PermanentlyDenied
    }
}

private fun shouldShowCameraRationale(activity: Activity): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.CAMERA,
    )

private fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.openAppSettings() {
    val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
