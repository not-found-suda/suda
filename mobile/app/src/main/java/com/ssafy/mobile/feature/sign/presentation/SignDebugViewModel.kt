@file:Suppress("TooManyFunctions")

package com.ssafy.mobile.feature.sign.presentation

import android.os.SystemClock
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.model.SignRecognitionMetrics
import com.ssafy.mobile.core.vision.SignRecognitionConfig
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignDebugUiState(
    val isRunning: Boolean = false,
    val isModelReady: Boolean = false,
    val currentGloss: String = "-",
    val confidence: Float = 0f,
    val hasHands: Boolean = false,
    val poseLandmarkCount: Int = 0,
    val leftHandLandmarkCount: Int = 0,
    val rightHandLandmarkCount: Int = 0,
    val lipLandmarkCount: Int = 0,
    val sequenceFrameCount: Int = 0,
    val sequenceHandFrameCount: Int = 0,
    val cameraFrameCount: Long = 0L,
    val cameraFps: Double = 0.0,
    val mediaPipeMs: Double = 0.0,
    val tfliteInferenceMs: Double = 0.0,
    val pipelineLatencyMs: Double = 0.0,
    val analysisImageSize: IntSize = IntSize.Zero,
    val cameraSettings: CameraAnalysisSettings = CameraAnalysisSettings(),
    val recognitionConfig: SignRecognitionConfig = SignRecognitionConfig(),
    val errorMessage: String? = null,
)

@HiltViewModel
class SignDebugViewModel
    @Inject
    constructor(
        private val signRecognitionEngine: SignRecognitionEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SignDebugUiState())
        val uiState: StateFlow<SignDebugUiState> = _uiState.asStateFlow()
        private var lastCameraMetricsUpdateAtMs = 0L
        private var lastRecognitionMetricsUpdateAtMs = 0L

        init {
            signRecognitionEngine.updateConfig(_uiState.value.recognitionConfig)
            viewModelScope.launch {
                signRecognitionEngine.events.collect(::handleEvent)
            }
        }

        fun start() {
            _uiState.update {
                it.copy(
                    isRunning = true,
                    errorMessage = null,
                    currentGloss = "-",
                    confidence = 0f,
                )
            }
            signRecognitionEngine.start()
        }

        fun stop() {
            signRecognitionEngine.stop()
            _uiState.update { it.copy(isRunning = false) }
        }

        fun onLandmarkFrame(frame: LandmarkFrameResult) {
            if (_uiState.value.isRunning) {
                signRecognitionEngine.submitFrame(frame)
            }
        }

        fun onCameraMetrics(metrics: CameraPerformanceMetrics) {
            if (!shouldUpdateCameraMetrics(metrics)) return

            _uiState.update { current ->
                current.copy(
                    cameraFrameCount = metrics.frameCount ?: current.cameraFrameCount,
                    cameraFps = metrics.cameraFps ?: current.cameraFps,
                    mediaPipeMs = metrics.mediaPipeMs ?: current.mediaPipeMs,
                    pipelineLatencyMs = metrics.pipelineLatencyMs ?: current.pipelineLatencyMs,
                    analysisImageSize = metrics.analysisImageSize ?: current.analysisImageSize,
                )
            }
        }

        private fun shouldUpdateCameraMetrics(metrics: CameraPerformanceMetrics): Boolean {
            if (metrics.frameCount != null || metrics.cameraFps != null) {
                lastCameraMetricsUpdateAtMs = SystemClock.elapsedRealtime()
                return true
            }

            return shouldUpdateDebugMetrics(
                lastUpdatedAtMs = lastCameraMetricsUpdateAtMs,
                onUpdate = { lastCameraMetricsUpdateAtMs = it },
            )
        }

        fun cycleResolution() {
            val currentIndex =
                RESOLUTION_OPTIONS.indexOf(
                    _uiState.value.cameraSettings.targetResolution,
                )
            val nextIndex = (currentIndex + 1).floorMod(RESOLUTION_OPTIONS.size)
            val nextResolution = RESOLUTION_OPTIONS[nextIndex]
            updateCameraSettings {
                it.copy(targetResolution = nextResolution)
            }
        }

        fun cycleTargetFps() {
            val currentIndex =
                TARGET_FPS_OPTIONS.indexOf(
                    _uiState.value.cameraSettings.targetFps,
                )
            val nextIndex = (currentIndex + 1).floorMod(TARGET_FPS_OPTIONS.size)
            val nextFps = TARGET_FPS_OPTIONS[nextIndex]
            updateCameraSettings {
                it.copy(targetFps = nextFps)
            }
        }

        fun cycleAnalysisFrameInterval() {
            val currentIndex =
                FRAME_INTERVAL_OPTIONS.indexOf(
                    _uiState.value.cameraSettings.analysisFrameInterval,
                )
            val nextIndex = (currentIndex + 1).floorMod(FRAME_INTERVAL_OPTIONS.size)
            val nextInterval = FRAME_INTERVAL_OPTIONS[nextIndex]
            updateCameraSettings {
                it.copy(analysisFrameInterval = nextInterval)
            }
        }

        fun cycleThreshold() {
            val currentIndex =
                THRESHOLD_OPTIONS.indexOf(
                    _uiState.value.recognitionConfig.confidenceThreshold,
                )
            val nextIndex = (currentIndex + 1).floorMod(THRESHOLD_OPTIONS.size)
            val nextThreshold = THRESHOLD_OPTIONS[nextIndex]
            updateRecognitionConfig {
                it.copy(confidenceThreshold = nextThreshold)
            }
        }

        fun cycleSmoothing() {
            val currentIndex =
                SMOOTHING_OPTIONS.indexOf(
                    _uiState.value.recognitionConfig.smoothingWindowSize,
                )
            val nextIndex = (currentIndex + 1).floorMod(SMOOTHING_OPTIONS.size)
            val nextWindow = SMOOTHING_OPTIONS[nextIndex]
            updateRecognitionConfig {
                it.copy(
                    smoothingWindowSize = nextWindow,
                    smoothingRequiredVotes = (nextWindow / 2 + 1).coerceAtMost(nextWindow),
                )
            }
        }

        private fun handleEvent(event: SignRecognitionEvent) {
            when (event) {
                SignRecognitionEvent.Ready -> _uiState.update { it.copy(isModelReady = true) }
                SignRecognitionEvent.Started -> _uiState.update { it.copy(isRunning = true) }
                SignRecognitionEvent.Stopped ->
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            currentGloss = "-",
                            confidence = 0f,
                        )
                    }
                is SignRecognitionEvent.Prediction ->
                    _uiState.update {
                        it.copy(
                            currentGloss = event.gloss,
                            confidence = event.confidence,
                        )
                    }
                is SignRecognitionEvent.Metrics -> updateRecognitionMetrics(event.snapshot)
                is SignRecognitionEvent.Error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = event.message,
                            isRunning = false,
                        )
                    }
                SignRecognitionEvent.ModelLoading ->
                    _uiState.update {
                        it.copy(isModelReady = false)
                    }
                SignRecognitionEvent.NoHandsDetected ->
                    _uiState.update {
                        it.copy(hasHands = false)
                    }
                is SignRecognitionEvent.Utterance -> Unit
            }
        }

        private fun updateRecognitionMetrics(metrics: SignRecognitionMetrics) {
            if (!shouldUpdateRecognitionMetrics()) return

            _uiState.update { current ->
                current.copy(
                    currentGloss = metrics.currentGloss ?: current.currentGloss,
                    confidence = metrics.confidence ?: current.confidence,
                    hasHands = metrics.hasHands,
                    poseLandmarkCount = metrics.poseLandmarkCount,
                    leftHandLandmarkCount = metrics.leftHandLandmarkCount,
                    rightHandLandmarkCount = metrics.rightHandLandmarkCount,
                    lipLandmarkCount = metrics.lipLandmarkCount,
                    sequenceFrameCount = metrics.sequenceFrameCount,
                    sequenceHandFrameCount = metrics.sequenceHandFrameCount,
                    tfliteInferenceMs = metrics.tfliteInferenceMs ?: current.tfliteInferenceMs,
                )
            }
        }

        private fun shouldUpdateRecognitionMetrics(): Boolean =
            shouldUpdateDebugMetrics(
                lastUpdatedAtMs = lastRecognitionMetricsUpdateAtMs,
                onUpdate = { lastRecognitionMetricsUpdateAtMs = it },
            )

        private fun shouldUpdateDebugMetrics(
            lastUpdatedAtMs: Long,
            onUpdate: (Long) -> Unit,
        ): Boolean {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastUpdatedAtMs < DEBUG_METRICS_UPDATE_INTERVAL_MILLIS) {
                return false
            }
            onUpdate(nowMs)
            return true
        }

        private fun updateCameraSettings(
            transform: (CameraAnalysisSettings) -> CameraAnalysisSettings,
        ) {
            _uiState.update { current ->
                current.copy(cameraSettings = transform(current.cameraSettings))
            }
        }

        private fun updateRecognitionConfig(
            transform: (SignRecognitionConfig) -> SignRecognitionConfig,
        ) {
            val nextConfig = transform(_uiState.value.recognitionConfig)
            signRecognitionEngine.updateConfig(nextConfig)
            _uiState.update {
                it.copy(
                    recognitionConfig = nextConfig,
                    currentGloss = "-",
                    confidence = 0f,
                )
            }
        }

        override fun onCleared() {
            signRecognitionEngine.stop()
            super.onCleared()
        }

        private fun Int.floorMod(modulus: Int): Int =
            if (this >= 0) {
                this % modulus
            } else {
                (this % modulus + modulus) % modulus
            }

        private companion object {
            val RESOLUTION_OPTIONS =
                listOf(
                    IntSize(640, 480),
                    IntSize(960, 540),
                    IntSize(1280, 720),
                )
            val TARGET_FPS_OPTIONS = listOf(10, 15, 24, 30)
            val FRAME_INTERVAL_OPTIONS = listOf(1, 2, 3, 4)
            val THRESHOLD_OPTIONS = listOf(0.70f, 0.75f, 0.80f, 0.90f)
            val SMOOTHING_OPTIONS = listOf(4, 6, 8)
            const val DEBUG_METRICS_UPDATE_INTERVAL_MILLIS = 100L
        }
    }
