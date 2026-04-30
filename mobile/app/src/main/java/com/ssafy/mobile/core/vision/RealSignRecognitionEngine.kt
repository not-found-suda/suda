package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureEncoder
import com.ssafy.mobile.core.vision.feature.SignSequenceBuffer
import com.ssafy.mobile.core.vision.inference.FakeSignInferenceAdapter
import com.ssafy.mobile.core.vision.inference.SignInferenceAdapter
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class RealSignRecognitionEngine(
    private val featureEncoder: LandmarkFeatureEncoder = LandmarkFeatureEncoder(),
    private val sequenceBuffer: SignSequenceBuffer = SignSequenceBuffer(),
    private val inferenceAdapter: SignInferenceAdapter = FakeSignInferenceAdapter(),
    private val noHandsDetectionTracker: NoHandsDetectionTracker = NoHandsDetectionTracker(),
    private val predictionStabilizer: SignPredictionStabilizer = SignPredictionStabilizer(),
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
        val result = sequence?.let { input -> inferenceAdapter.predict(input) }
        result?.let { inferenceResult ->
            logger.logInferenceResult(
                sequenceSize = sequence.size,
                gloss = inferenceResult.gloss,
                confidence = inferenceResult.confidence,
            )
        }
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
