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
) : SignRecognitionEngine {
    @Inject
    constructor() : this(
        featureEncoder = LandmarkFeatureEncoder(),
        sequenceBuffer = SignSequenceBuffer(),
        inferenceAdapter = FakeSignInferenceAdapter(),
        noHandsDetectionTracker = NoHandsDetectionTracker(),
        predictionStabilizer = SignPredictionStabilizer(),
    )

    private val isStarted = AtomicBoolean(false)
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
        _events.tryEmit(SignRecognitionEvent.Started)
        _events.tryEmit(SignRecognitionEvent.Ready)
    }

    override fun stop() {
        if (!isStarted.compareAndSet(true, false)) {
            return
        }

        resetSessionState()
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

    fun onLandmarkFrame(frame: LandmarkFrameResult) {
        submitFrame(frame)
    }

    private fun processFrame(frame: LandmarkFrameResult) {
        if (handleNoHandsFrame(frame)) {
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
        sequenceBuffer.add(feature)
        val sequence = sequenceBuffer.buildSequenceInput() ?: return null
        val result = inferenceAdapter.predict(sequence)
        return predictionStabilizer
            .onPrediction(result)
            ?.let { stablePrediction ->
                SignRecognitionEvent.Prediction(
                    gloss = stablePrediction.gloss,
                    confidence = stablePrediction.confidence,
                    timestampMs = frame.timestampMs,
                )
            }
    }

    private fun handleNoHandsFrame(frame: LandmarkFrameResult): Boolean {
        val noHandsEvent = noHandsDetectionTracker.onFrame(frame)
        if (frame.hasHands) {
            return false
        }

        resetRecognitionState()
        noHandsEvent?.let { event -> _events.tryEmit(event) }
        return true
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
        featureEncoder.reset()
        sequenceBuffer.clear()
        predictionStabilizer.reset()
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64
    }
}
