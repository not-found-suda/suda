package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.model.SignRecognitionMetrics
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureEncoder
import com.ssafy.mobile.core.vision.feature.SignSequenceBuffer
import com.ssafy.mobile.core.vision.inference.FakeSignInferenceAdapter
import com.ssafy.mobile.core.vision.inference.SignInferenceAdapter
import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.system.measureNanoTime
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RealSignRecognitionEngine(
    private val featureEncoder: LandmarkFeatureEncoder = LandmarkFeatureEncoder(),
    private var sequenceBuffer: SignSequenceBuffer = SignSequenceBuffer(),
    private val inferenceAdapter: SignInferenceAdapter = FakeSignInferenceAdapter(),
    private val noHandsDetectionTracker: NoHandsDetectionTracker = NoHandsDetectionTracker(),
    private var predictionStabilizer: SignPredictionStabilizer = SignPredictionStabilizer(),
    private val logger: SignPipelineLogger = SignPipelineLogger(),
) : SignRecognitionEngine {
    @Inject
    constructor(
        inferenceAdapter: SignInferenceAdapter,
    ) : this(
        featureEncoder = LandmarkFeatureEncoder(),
        sequenceBuffer = SignSequenceBuffer(),
        inferenceAdapter = inferenceAdapter,
        noHandsDetectionTracker = NoHandsDetectionTracker(),
        predictionStabilizer = SignPredictionStabilizer(),
        logger = SignPipelineLogger(),
    )

    private val isStarted = AtomicBoolean(false)
    private var config = SignRecognitionConfig()
    private var hasSeenHandsInSegment = false
    private val _events =
        MutableSharedFlow<SignRecognitionEvent>(
            extraBufferCapacity = EVENT_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val events: Flow<SignRecognitionEvent> = _events.asSharedFlow()

    override fun start() {
        if (!isStarted.compareAndSet(false, true)) {
            return
        }

        resetSessionState()
        logger.logEngineStarted()
        logger.logHandForwardFillMode(featureEncoder.isHandForwardFillEnabled)
        _events.tryEmit(SignRecognitionEvent.Started)
        _events.tryEmit(SignRecognitionEvent.Ready)
    }

    override fun stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return
        }

        resetSessionState()
        logger.logEngineStopped()
        _events.tryEmit(SignRecognitionEvent.Stopped)
    }

    override fun updateConfig(config: SignRecognitionConfig) {
        this.config = config
        sequenceBuffer =
            SignSequenceBuffer(
                sequenceLength = config.sequenceLength,
                minimumHandFrameRatio = config.minimumHandFrameRatio,
            )
        predictionStabilizer =
            SignPredictionStabilizer(
                confidenceThreshold = config.confidenceThreshold,
                marginThreshold = config.marginThreshold,
                windowSize = config.smoothingWindowSize,
                requiredVotes = config.smoothingRequiredVotes,
                emitCooldownMs = config.emitCooldownMs,
            )
        resetRecognitionState()
    }

    override fun submitFrame(frame: LandmarkFrameResult) {
        if (!isStarted.get()) {
            return
        }

        runCatching { processFrame(frame) }
            .onFailure { throwable ->
                _events.tryEmit(
                    SignRecognitionEvent.Error(
                        message = "수어 인식 처리 중 오류가 발생했습니다.",
                        cause = throwable,
                    ),
                )
            }
    }

    private fun processFrame(frame: LandmarkFrameResult) {
        logger.logFrameState(frame)
        if (shouldSkipFrame(frame)) {
            return
        }

        createPredictionEvent(frame)?.let { event ->
            _events.tryEmit(event)
        }
    }

    private fun createPredictionEvent(
        frame: LandmarkFrameResult,
    ): SignRecognitionEvent.Prediction? {
        val feature = featureEncoder.encode(frame)
        logger.logFeatureProbe(feature.probe)
        sequenceBuffer.add(feature)
        val sequence = sequenceBuffer.buildReadySequenceInput(logger)
        var tfliteInferenceMs: Double? = null
        var result: SignInferenceResult? = null
        if (sequence != null) {
            val elapsedNanos =
                measureNanoTime {
                    result = inferenceAdapter.predict(sequence)
                }
            tfliteInferenceMs = elapsedNanos / NANOS_PER_MILLIS
        }
        result?.let { inferenceResult ->
            logger.logInferenceResult(
                sequenceSize = sequence?.size ?: 0,
                gloss = inferenceResult.gloss,
                confidence = inferenceResult.confidence,
            )
        }
        _events.tryEmit(
            SignRecognitionEvent.Metrics(
                snapshot =
                    SignRecognitionMetrics(
                        timestampMs = frame.timestampMs,
                        currentGloss = result?.gloss,
                        confidence = result?.confidence,
                        hasHands = frame.hasHands,
                        poseLandmarkCount = frame.pose.landmarks.size,
                        leftHandLandmarkCount = frame.leftHand.landmarks.size,
                        rightHandLandmarkCount = frame.rightHand.landmarks.size,
                        lipLandmarkCount = frame.lips.landmarks.size,
                        sequenceFrameCount = sequenceBuffer.size,
                        sequenceHandFrameCount = sequenceBuffer.handFrameCount,
                        tfliteInferenceMs = tfliteInferenceMs,
                        config = config,
                    ),
            ),
        )
        val stablePrediction =
            result?.let { inferenceResult ->
                predictionStabilizer.onPrediction(
                    result = inferenceResult,
                    timestampMs = frame.timestampMs,
                )
            }
        return stablePrediction?.let { prediction ->
            logger.logPredictionEmitted(
                gloss = prediction.gloss,
                confidence = prediction.confidence,
            )
            SignRecognitionEvent.Prediction(
                gloss = prediction.gloss,
                confidence = prediction.confidence,
                timestampMs = frame.timestampMs,
            )
        }
    }

    private fun shouldSkipFrame(frame: LandmarkFrameResult): Boolean {
        var shouldSkip = false
        if (frame.hasHands) {
            hasSeenHandsInSegment = true
            noHandsDetectionTracker.onFrame(frame)
        } else if (!hasSeenHandsInSegment) {
            noHandsDetectionTracker.reset()
            shouldSkip = true
        } else {
            val noHandsEvent = noHandsDetectionTracker.onFrame(frame)
            if (noHandsEvent != null) {
                resetRecognitionState()
                logger.logNoHandsDetected()
                _events.tryEmit(noHandsEvent)
                shouldSkip = true
            }
        }

        return shouldSkip
    }

    fun close() {
        stop()
        inferenceAdapter.close()
    }

    private fun resetSessionState() {
        noHandsDetectionTracker.reset()
        resetRecognitionState()
    }

    private fun resetRecognitionState() {
        hasSeenHandsInSegment = false
        featureEncoder.reset()
        sequenceBuffer.clear()
        predictionStabilizer.reset()
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64
        const val NANOS_PER_MILLIS = 1_000_000.0
    }
}

private fun SignSequenceBuffer.buildReadySequenceInput(logger: SignPipelineLogger): FloatArray? =
    when {
        !hasEnoughFrames -> {
            logger.logSequenceBuffering(size)
            null
        }
        !hasEnoughHandFrames -> {
            logger.logSequenceWaitingForHands(
                handFrames = handFrameCount,
                totalFrames = size,
            )
            null
        }
        else -> buildSequenceInput()
    }
