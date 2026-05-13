@file:Suppress(
    "CyclomaticComplexMethod",
    "LargeClass",
    "LongMethod",
    "MagicNumber",
    "TooManyFunctions",
)

package com.ssafy.mobile.feature.sign.presentation

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.model.SignRecognitionMetrics
import com.ssafy.mobile.core.vision.SignRecognitionConfig
import com.ssafy.mobile.core.vision.SignRecognitionEngine
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureEncoder
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureSequenceResampler
import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.MediaPipeHolisticLandmarkExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val isReplayRunning: Boolean = false,
    val replayVideoName: String = "-",
    val replayAttemptedFrameCount: Int = 0,
    val replayProcessedFrameCount: Int = 0,
    val replayTotalFrameCount: Int = 0,
    val replayDurationMs: Long = 0L,
    val replayRawPredictions: List<String> = emptyList(),
    val replayPredictions: List<String> = emptyList(),
    val replayWholeVideoPrediction: String? = null,
    val replayStatusMessage: String? = null,
    val debugFrameSaveMessage: String? = null,
    val isAnalysisRecording: Boolean = false,
    val analysisRecordingMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class SignDebugViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val appContext: Context,
        private val signRecognitionEngine: SignRecognitionEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SignDebugUiState())
        val uiState: StateFlow<SignDebugUiState> = _uiState.asStateFlow()
        private var lastCameraMetricsUpdateAtMs = 0L
        private var lastRecognitionMetricsUpdateAtMs = 0L
        private var replayJob: Job? = null
        private var latestAnalysisBitmap: Bitmap? = null
        private var analysisRecorder: AnalysisFrameVideoRecorder? = null

        init {
            signRecognitionEngine.updateConfig(_uiState.value.recognitionConfig)
            viewModelScope.launch {
                signRecognitionEngine.events.collect(::handleEvent)
            }
        }

        fun start() {
            stopVideoReplay()
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
            stopAnalysisRecording()
            replayJob?.cancel()
            replayJob = null
            signRecognitionEngine.stop()
            _uiState.update { it.copy(isRunning = false) }
        }

        fun startVideoReplay(uri: Uri) {
            replayJob?.cancel()
            replayJob =
                viewModelScope.launch(Dispatchers.Default) {
                    val videoName = resolveDisplayName(uri)
                    prepareVideoReplay(videoName)
                    runCatching {
                        replayVideo(uri)
                    }.fold(
                        onSuccess = { finishVideoReplay("영상 분석이 완료되었습니다.") },
                        onFailure = { throwable ->
                            if (throwable is CancellationException) {
                                finishVideoReplay("영상 분석이 중지되었습니다.")
                            } else {
                                finishVideoReplay(
                                    "영상 분석 중 오류가 발생했습니다: " +
                                        throwable.message.orEmpty(),
                                )
                            }
                        },
                    )
                }
        }

        fun stopVideoReplay() {
            replayJob?.cancel()
            replayJob = null
            if (_uiState.value.isReplayRunning) {
                signRecognitionEngine.stop()
                _uiState.update {
                    it.copy(
                        isReplayRunning = false,
                        replayStatusMessage = "영상 분석이 중지되었습니다.",
                    )
                }
            }
        }

        fun onLandmarkFrame(frame: LandmarkFrameResult) {
            if (_uiState.value.isRunning) {
                signRecognitionEngine.submitFrame(frame)
            }
        }

        fun onAnalysisFrame(frame: YuvAnalysisFrame) {
            latestAnalysisBitmap = frame.bitmap
            analysisRecorder?.record(frame.bitmap)
        }

        fun startAnalysisRecording() {
            if (analysisRecorder != null) {
                return
            }
            analysisRecorder = AnalysisFrameVideoRecorder(appContext)
            _uiState.update {
                it.copy(
                    isAnalysisRecording = true,
                    analysisRecordingMessage = "분석 영상 녹화 중",
                )
            }
        }

        fun stopAnalysisRecording() {
            val recorder = analysisRecorder ?: return
            analysisRecorder = null
            _uiState.update {
                it.copy(
                    isAnalysisRecording = false,
                    analysisRecordingMessage = "분석 영상 저장 중...",
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    recorder.stop()
                }.fold(
                    onSuccess = { savedPath ->
                        _uiState.update {
                            it.copy(analysisRecordingMessage = "영상 저장 완료: $savedPath")
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                analysisRecordingMessage =
                                    "영상 저장 실패: ${throwable.message.orEmpty()}",
                            )
                        }
                    },
                )
            }
        }

        fun cancelAnalysisRecording() {
            val recorder = analysisRecorder ?: return
            analysisRecorder = null
            _uiState.update {
                it.copy(
                    isAnalysisRecording = false,
                    analysisRecordingMessage = "영상 저장 취소 중...",
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    recorder.cancel()
                }.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(analysisRecordingMessage = "영상 저장을 취소했습니다.")
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                analysisRecordingMessage =
                                    "영상 저장 취소 실패: ${throwable.message.orEmpty()}",
                            )
                        }
                    },
                )
            }
        }

        fun saveCurrentAnalysisFrame() {
            val bitmap = latestAnalysisBitmap
            if (bitmap == null) {
                _uiState.update {
                    it.copy(debugFrameSaveMessage = "아직 저장할 분석 프레임이 없습니다.")
                }
                return
            }

            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    saveBitmapToPictures(bitmap)
                }.fold(
                    onSuccess = { savedName ->
                        _uiState.update {
                            it.copy(debugFrameSaveMessage = "저장 완료: $savedName")
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                debugFrameSaveMessage =
                                    "저장 실패: ${throwable.message.orEmpty()}",
                            )
                        }
                    },
                )
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

        private fun saveBitmapToPictures(bitmap: Bitmap): String {
            val fileName = "sign_debug_analysis_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values =
                    android.content.ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "Pictures/S14P31A404-debug",
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                val resolver = appContext.contentResolver
                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) {
                        "PNG encode failed"
                    }
                } ?: error("Could not open MediaStore output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Pictures/S14P31A404-debug/$fileName"
            }

            val directory = File(appContext.cacheDir, "sign-debug").apply { mkdirs() }
            val file = File(directory, fileName)
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) {
                    "PNG encode failed"
                }
            }
            return file.absolutePath
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

        fun toggleMirrorAnalysisInput() {
            updateCameraSettings {
                it.copy(mirrorAnalysisInput = !it.mirrorAnalysisInput)
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
                SignRecognitionEvent.Started ->
                    _uiState.update { current ->
                        current.copy(isRunning = current.isRunning && !current.isReplayRunning)
                    }
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
                        if (it.isReplayRunning) {
                            Log.d(
                                TAG_REPLAY,
                                "CONFIRMED ${event.timestampMs}ms " +
                                    "${event.gloss} (${formatPercent(event.confidence)})",
                            )
                        }
                        it.copy(
                            currentGloss = event.gloss,
                            confidence = event.confidence,
                            replayPredictions =
                                if (it.isReplayRunning) {
                                    appendReplayPrediction(
                                        predictions = it.replayPredictions,
                                        event = event,
                                    )
                                } else {
                                    it.replayPredictions
                                },
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
                is SignRecognitionEvent.Utterance ->
                    _uiState.update {
                        val gloss = event.glosses.joinToString(separator = " / ")
                        val sentenceType = event.sentenceType
                        val displayGloss =
                            when {
                                sentenceType.isNullOrBlank() -> "문장: $gloss"
                                else -> "문장: $gloss ($sentenceType)"
                            }
                        if (it.isReplayRunning) {
                            Log.d(
                                TAG_REPLAY,
                                "CONFIRMED ${event.endedAtMs}ms " +
                                    "$displayGloss (${formatPercent(event.confidence)})",
                            )
                        }
                        it.copy(
                            currentGloss = displayGloss,
                            confidence = event.confidence,
                            replayPredictions =
                                if (it.isReplayRunning) {
                                    appendReplayPrediction(
                                        predictions = it.replayPredictions,
                                        gloss = displayGloss,
                                        confidence = event.confidence,
                                    )
                                } else {
                                    it.replayPredictions
                                },
                        )
                    }
            }
        }

        private fun updateRecognitionMetrics(metrics: SignRecognitionMetrics) {
            if (!_uiState.value.isReplayRunning && !shouldUpdateRecognitionMetrics()) return

            _uiState.update { current ->
                val nextRawPredictions =
                    if (
                        SHOW_REPLAY_RAW_PREDICTIONS &&
                        current.isReplayRunning &&
                        metrics.currentGloss != null
                    ) {
                        appendReplayRawPrediction(
                            predictions = current.replayRawPredictions,
                            metrics = metrics,
                        )
                    } else {
                        current.replayRawPredictions
                    }
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
                    replayRawPredictions = nextRawPredictions,
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

        private suspend fun prepareVideoReplay(videoName: String) {
            withContext(Dispatchers.Main.immediate) {
                signRecognitionEngine.stop()
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isReplayRunning = true,
                        replayVideoName = videoName,
                        replayAttemptedFrameCount = 0,
                        replayProcessedFrameCount = 0,
                        replayTotalFrameCount = 0,
                        replayDurationMs = 0L,
                        replayRawPredictions = emptyList(),
                        replayPredictions = emptyList(),
                        replayWholeVideoPrediction = null,
                        replayStatusMessage = "영상 분석을 준비하고 있습니다.",
                        currentGloss = "-",
                        confidence = 0f,
                        errorMessage = null,
                    )
                }
            }
        }

        private fun replayVideo(uri: Uri) {
            val retriever = MediaMetadataRetriever()
            val extractor = MediaPipeHolisticLandmarkExtractor.create(appContext)
            val replayFrames = mutableListOf<LandmarkFrameResult>()
            try {
                retriever.setDataSource(appContext, uri)
                val durationMs =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 0L
                val metadataFrameCount = retriever.extractVideoFrameCount()
                val totalFrameCount = metadataFrameCount ?: durationMs.toReplayFrameCount()
                _uiState.update {
                    it.copy(
                        replayDurationMs = durationMs,
                        replayTotalFrameCount = totalFrameCount,
                        replayStatusMessage =
                            if (metadataFrameCount != null) {
                                "영상 프레임 순서대로 분석 중입니다."
                            } else {
                                "영상 분석 중입니다."
                            },
                    )
                }

                if (metadataFrameCount != null) {
                    replayVideoByFrameIndex(
                        retriever = retriever,
                        extractor = extractor,
                        durationMs = durationMs,
                        totalFrameCount = metadataFrameCount,
                        replayFrames = replayFrames,
                    )
                } else {
                    replayVideoByTimestamp(
                        retriever = retriever,
                        extractor = extractor,
                        durationMs = durationMs,
                        replayFrames = replayFrames,
                    )
                }
                if (replayJob?.isActive == true) {
                    classifyWholeReplayVideo(replayFrames)
                }
            } finally {
                extractor.close()
                retriever.release()
            }
        }

        private fun replayVideoByFrameIndex(
            retriever: MediaMetadataRetriever,
            extractor: MediaPipeHolisticLandmarkExtractor,
            durationMs: Long,
            totalFrameCount: Int,
            replayFrames: MutableList<LandmarkFrameResult>,
        ) {
            Log.d(
                TAG_REPLAY_FRAME,
                "mode=frame-index frameCount=$totalFrameCount duration=${durationMs}ms",
            )

            var processedFrameCount = 0
            for (frameIndex in 0 until totalFrameCount) {
                if (replayJob?.isActive != true) return
                replayJob?.ensureActive()
                _uiState.update {
                    it.copy(replayAttemptedFrameCount = frameIndex + 1)
                }

                val bitmap =
                    runCatching {
                        retriever.getFrameAtIndex(frameIndex)
                    }.getOrNull() ?: continue
                val timestampMs =
                    frameIndex.toReplayTimestampMs(
                        durationMs = durationMs,
                        totalFrameCount = totalFrameCount,
                    )
                try {
                    val landmarkFrame =
                        processReplayBitmap(
                            bitmap = bitmap,
                            timestampMs = timestampMs,
                            extractor = extractor,
                            processedFrameIndex = processedFrameCount,
                        )
                    replayFrames += landmarkFrame
                    processedFrameCount += 1
                    _uiState.update {
                        it.copy(replayProcessedFrameCount = processedFrameCount)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }

        private fun replayVideoByTimestamp(
            retriever: MediaMetadataRetriever,
            extractor: MediaPipeHolisticLandmarkExtractor,
            durationMs: Long,
            replayFrames: MutableList<LandmarkFrameResult>,
        ) {
            Log.d(
                TAG_REPLAY_FRAME,
                "mode=timestamp-seek interval=${VIDEO_REPLAY_FRAME_INTERVAL_MILLIS}ms " +
                    "duration=${durationMs}ms",
            )

            var processedFrameCount = 0
            var attemptedFrameCount = 0
            var timestampMs = 0L
            while (timestampMs <= durationMs && replayJob?.isActive == true) {
                replayJob?.ensureActive()
                attemptedFrameCount += 1
                _uiState.update {
                    it.copy(replayAttemptedFrameCount = attemptedFrameCount)
                }
                retriever
                    .getFrameAtTime(
                        timestampMs * MICROS_PER_MILLIS,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    )?.let { bitmap ->
                        try {
                            val landmarkFrame =
                                processReplayBitmap(
                                    bitmap = bitmap,
                                    timestampMs = timestampMs,
                                    extractor = extractor,
                                    processedFrameIndex = processedFrameCount,
                                )
                            replayFrames += landmarkFrame
                            processedFrameCount += 1
                            _uiState.update {
                                it.copy(replayProcessedFrameCount = processedFrameCount)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                timestampMs += VIDEO_REPLAY_FRAME_INTERVAL_MILLIS
            }
        }

        private fun processReplayBitmap(
            bitmap: Bitmap,
            timestampMs: Long,
            extractor: MediaPipeHolisticLandmarkExtractor,
            processedFrameIndex: Int,
        ): LandmarkFrameResult {
            val landmarkFrame =
                extractor.detect(
                    bitmap = bitmap,
                    timestampMs = timestampMs,
                )
            logReplayDecodedFrame(
                index = processedFrameIndex + 1,
                frame = landmarkFrame,
            )
            return landmarkFrame
        }

        private fun classifyWholeReplayVideo(frames: List<LandmarkFrameResult>) {
            val result = WholeVideoIntentClassifier(appContext).classify(frames)
            val prediction = result.formatReplayPrediction()
            val featureSaveMessage =
                runCatching {
                    saveReplayFeaturesToDownloads(
                        frames = frames,
                        videoName = _uiState.value.replayVideoName,
                    )
                }.onSuccess { savedPath ->
                    Log.d(TAG_REPLAY, "FEATURE_CSV saved=$savedPath")
                }.onFailure { throwable ->
                    Log.w(TAG_REPLAY, "FEATURE_CSV save failed.", throwable)
                }.getOrNull()
            Log.d(
                TAG_REPLAY,
                "WHOLE_VIDEO $prediction " +
                    "top2=${result.secondGloss ?: "-"} " +
                    formatPercent(result.secondConfidence ?: 0f),
            )
            _uiState.update {
                it.copy(
                    replayWholeVideoPrediction = prediction,
                    replayStatusMessage =
                        featureSaveMessage?.let { savedPath ->
                            "Feature CSV saved: $savedPath"
                        } ?: it.replayStatusMessage,
                    currentGloss = result.gloss,
                    confidence = result.confidence,
                )
            }
        }

        private fun saveReplayFeaturesToDownloads(
            frames: List<LandmarkFrameResult>,
            videoName: String,
        ): String {
            val encoder = LandmarkFeatureEncoder()
            val featureFrames = frames.map(encoder::encode)
            val safeVideoName =
                videoName
                    .substringBeforeLast('.', missingDelimiterValue = videoName)
                    .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                    .ifBlank { "replay" }
            val fileName = "sign_replay_features_${safeVideoName}_${System.currentTimeMillis()}.csv"
            val modelInputFileName =
                "sign_model_input_30x332_${safeVideoName}_${System.currentTimeMillis()}.csv"
            val csvText =
                buildString {
                    append("frame_index,timestamp_ms,has_hands")
                    repeat(FEATURE_DIMENSION) { index ->
                        append(",f")
                        append(index.toString().padStart(3, '0'))
                    }
                    appendLine()
                    featureFrames.forEachIndexed { frameIndex, frame ->
                        append(frameIndex)
                        append(',')
                        append(frame.timestampMs)
                        append(',')
                        append(if (frame.hasHands) 1 else 0)
                        frame.values.forEach { value ->
                            append(',')
                            append(String.format(Locale.US, "%.8f", value))
                        }
                        appendLine()
                    }
                }
            val modelInputCsvText =
                LandmarkFeatureSequenceResampler
                    .resampleToModelInput(featureFrames)
                    .toModelInputCsvText()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val replayFeaturePath =
                    saveCsvToDownloads(
                        fileName = fileName,
                        csvText = csvText,
                    )
                val modelInputPath =
                    saveCsvToDownloads(
                        fileName = modelInputFileName,
                        csvText = modelInputCsvText,
                    )
                return "$replayFeaturePath, $modelInputPath"
            }

            val directory =
                File(
                    appContext.getExternalFilesDir(null) ?: appContext.filesDir,
                    "S14P31A404-debug",
                ).apply { mkdirs() }
            val file = File(directory, fileName)
            file.writeText(csvText, Charsets.UTF_8)
            val modelInputFile = File(directory, modelInputFileName)
            modelInputFile.writeText(modelInputCsvText, Charsets.UTF_8)
            return "${file.absolutePath}, ${modelInputFile.absolutePath}"
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun saveCsvToDownloads(
            fileName: String,
            csvText: String,
        ): String {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/S14P31A404-debug")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val resolver = appContext.contentResolver
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(csvText)
            } ?: error("Could not open MediaStore output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Download/S14P31A404-debug/$fileName"
        }

        private fun FloatArray.toModelInputCsvText(): String =
            buildString {
                append("frame_index")
                repeat(FEATURE_DIMENSION) { index ->
                    append(",f")
                    append(index.toString().padStart(3, '0'))
                }
                appendLine()

                val frameCount = size / FEATURE_DIMENSION
                repeat(frameCount) { frameIndex ->
                    append(frameIndex)
                    val frameOffset = frameIndex * FEATURE_DIMENSION
                    repeat(FEATURE_DIMENSION) { featureIndex ->
                        append(',')
                        append(
                            String.format(
                                Locale.US,
                                "%.8f",
                                this@toModelInputCsvText[frameOffset + featureIndex],
                            ),
                        )
                    }
                    appendLine()
                }
            }

        private fun logReplayDecodedFrame(
            index: Int,
            frame: LandmarkFrameResult,
        ) {
            Log.d(
                TAG_REPLAY_FRAME,
                "#$index ${frame.timestampMs}ms " +
                    "hasHands=${frame.hasHands} " +
                    "pose=${frame.pose.landmarks.size} " +
                    "left=${frame.leftHand.landmarks.size} " +
                    "right=${frame.rightHand.landmarks.size} " +
                    "face=${frame.face.landmarks.size} " +
                    "lips=${frame.lips.landmarks.size}",
            )
        }

        private fun finishVideoReplay(message: String) {
            signRecognitionEngine.stop()
            replayJob = null
            _uiState.update {
                it.copy(
                    isReplayRunning = false,
                    isRunning = false,
                    replayStatusMessage = message,
                )
            }
        }

        private fun resolveDisplayName(uri: Uri): String =
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                } ?: uri.lastPathSegment ?: "선택한 영상"

        private fun Long.toReplayFrameCount(): Int =
            if (this <= 0L) {
                0
            } else {
                (this / VIDEO_REPLAY_FRAME_INTERVAL_MILLIS + 1L).toInt()
            }

        private fun MediaMetadataRetriever.extractVideoFrameCount(): Int? =
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }

        private fun Int.toReplayTimestampMs(
            durationMs: Long,
            totalFrameCount: Int,
        ): Long =
            when {
                durationMs <= 0L -> this * VIDEO_REPLAY_FRAME_INTERVAL_MILLIS
                totalFrameCount <= 1 -> 0L
                else -> durationMs * this / (totalFrameCount - 1)
            }

        private fun appendReplayPrediction(
            predictions: List<String>,
            event: SignRecognitionEvent.Prediction,
        ): List<String> =
            appendReplayPrediction(
                predictions = predictions,
                gloss = event.gloss,
                confidence = event.confidence,
            )

        private fun appendReplayPrediction(
            predictions: List<String>,
            gloss: String,
            confidence: Float,
        ): List<String> =
            (
                predictions +
                    "$gloss (${formatPercent(confidence)})"
            ).takeLast(MAX_REPLAY_PREDICTION_LOG_SIZE)

        private fun SignInferenceResult.formatReplayPrediction(): String =
            "$gloss (${formatPercent(confidence)})"

        private fun appendReplayRawPrediction(
            predictions: List<String>,
            metrics: SignRecognitionMetrics,
        ): List<String> {
            val confidence = metrics.confidence ?: 0f
            val secondConfidence = metrics.secondConfidence ?: 0f
            val top1Index = metrics.classIndex?.toString() ?: "-"
            val top2Label = metrics.secondGloss ?: "-"
            val entry =
                "#${predictions.size + 1} " +
                    "${metrics.timestampMs}ms " +
                    "top1=[$top1Index]${metrics.currentGloss} " +
                    formatPercent(confidence) +
                    " top2=$top2Label " +
                    formatPercent(secondConfidence) +
                    " margin=${formatPercent(metrics.margin ?: 0f)} " +
                    "seq=${metrics.sequenceFrameCount}/" +
                    "${metrics.config.sequenceLength}, " +
                    "hands=${metrics.sequenceHandFrameCount}"
            if (LOG_REPLAY_RAW_PREDICTIONS) {
                Log.d(TAG_REPLAY, entry)
                metrics.featureProbe?.let { probe ->
                    Log.d(
                        TAG_REPLAY,
                        "FEATURE #${predictions.size + 1} " +
                            "${metrics.timestampMs}ms " +
                            "head=${probe.head.formatFeatureSlice()} " +
                            "face126=${probe.faceStart.formatFeatureSlice()} " +
                            "pose261=${probe.poseStart.formatFeatureSlice()} " +
                            "dist282=${probe.distanceStart.formatFeatureSlice()}",
                    )
                }
                if (metrics.sequenceTimestampsMs.isNotEmpty()) {
                    Log.d(
                        TAG_REPLAY,
                        "SEQUENCE #${predictions.size + 1} " +
                            "${metrics.timestampMs}ms " +
                            "timestamps=${metrics.sequenceTimestampsMs.formatTimestampList()}",
                    )
                }
            }
            return (predictions + entry).takeLast(MAX_REPLAY_RAW_PREDICTION_LOG_SIZE)
        }

        private fun formatPercent(value: Float): String =
            String.format(Locale.US, "%.1f%%", value * PERCENT_MULTIPLIER)

        private fun List<Float>.formatFeatureSlice(): String =
            joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
            ) { value -> String.format(Locale.US, "%.5f", value) }

        private fun List<Long>.formatTimestampList(): String =
            joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
            )

        override fun onCleared() {
            analysisRecorder?.let { recorder ->
                runCatching { recorder.stop() }
            }
            analysisRecorder = null
            replayJob?.cancel()
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
                    IntSize(640, 360),
                    IntSize(960, 540),
                    IntSize(1280, 720),
                )
            val TARGET_FPS_OPTIONS = listOf(10, 15, 24, 30)
            val FRAME_INTERVAL_OPTIONS = listOf(1, 2, 3, 4)
            val THRESHOLD_OPTIONS = listOf(0.70f, 0.75f, 0.80f, 0.90f)
            val SMOOTHING_OPTIONS = listOf(4, 6, 8)
            const val DEBUG_METRICS_UPDATE_INTERVAL_MILLIS = 100L
            const val VIDEO_REPLAY_FRAME_INTERVAL_MILLIS = 33L
            const val MICROS_PER_MILLIS = 1_000L
            const val PNG_QUALITY = 100
            const val FEATURE_DIMENSION = 332
            const val MAX_REPLAY_PREDICTION_LOG_SIZE = 12
            const val MAX_REPLAY_RAW_PREDICTION_LOG_SIZE = 500
            const val PERCENT_MULTIPLIER = 100f
            const val SHOW_REPLAY_RAW_PREDICTIONS = false
            const val LOG_REPLAY_RAW_PREDICTIONS = false
            const val TAG_REPLAY = "SignReplay"
            const val TAG_REPLAY_FRAME = "SignReplayFrame"
        }
    }
