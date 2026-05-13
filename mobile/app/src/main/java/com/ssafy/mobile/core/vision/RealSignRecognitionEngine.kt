@file:Suppress("MagicNumber", "ReturnCount", "TooManyFunctions")

package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignFeatureProbeSlices
import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.model.SignRecognitionMetrics
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureEncoder
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureFrame
import com.ssafy.mobile.core.vision.feature.SignFeatureSpec
import com.ssafy.mobile.core.vision.feature.SignSequenceBuffer
import com.ssafy.mobile.core.vision.inference.FakeSignInferenceAdapter
import com.ssafy.mobile.core.vision.inference.SignInferenceAdapter
import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.inference.SignModelContract
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.LandmarkPoint
import com.ssafy.mobile.core.vision.wordspotting.NoOpWordSpottingScanner
import com.ssafy.mobile.core.vision.wordspotting.WordSpottingScanner
import java.util.ArrayDeque
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
    private val wordSpottingScanner: WordSpottingScanner = NoOpWordSpottingScanner,
    private val noHandsDetectionTracker: NoHandsDetectionTracker = NoHandsDetectionTracker(),
    private var predictionStabilizer: SignPredictionStabilizer = SignPredictionStabilizer(),
    private val logger: SignPipelineLogger = SignPipelineLogger(),
) : SignRecognitionEngine {
    @Inject
    constructor(
        inferenceAdapter: SignInferenceAdapter,
        wordSpottingScanner: WordSpottingScanner,
    ) : this(
        featureEncoder = LandmarkFeatureEncoder(),
        sequenceBuffer = SignSequenceBuffer(),
        inferenceAdapter = inferenceAdapter,
        wordSpottingScanner = wordSpottingScanner,
        noHandsDetectionTracker = NoHandsDetectionTracker(),
        predictionStabilizer = SignPredictionStabilizer(),
        logger = SignPipelineLogger(),
    )

    private val isStarted = AtomicBoolean(false)
    private var config = SignRecognitionConfig()
    private var hasSeenHandsInSegment = false
    private var missingHandFrames = 0
    private var sentenceIsQuestion = false
    private val segmentFrames = mutableListOf<LandmarkFeatureFrame>()
    private val sentenceBuffer = mutableListOf<StableSignPrediction>()
    private val neutralEyebrowGaps = ArrayDeque<Float>(NEUTRAL_FACE_WINDOW)
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
        logger.logInferenceAdapter(
            adapterName = inferenceAdapter::class.java.simpleName,
            supportsFullSegmentInference = inferenceAdapter.supportsFullSegmentInference,
        )
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
        if (!frame.hasHands) {
            frame.normalizedEyebrowGap()?.let(::addNeutralEyebrowGap)
            processNoHandsFrame(frame)
            return
        }

        processHandFrame(frame)
    }

    private fun processHandFrame(frame: LandmarkFrameResult) {
        if (!hasSeenHandsInSegment) {
            hasSeenHandsInSegment = true
        }
        missingHandFrames = 0
        updateSentenceType(frame)
        noHandsDetectionTracker.reset()
        processFeatureFrame(frame)
    }

    private fun processFeatureFrame(frame: LandmarkFrameResult) {
        val feature = featureEncoder.encode(frame)
        logger.logFeatureProbe(feature.probe)
        segmentFrames += feature
        sequenceBuffer.add(feature)
        _events.tryEmit(
            SignRecognitionEvent.Metrics(
                snapshot =
                    SignRecognitionMetrics(
                        timestampMs = frame.timestampMs,
                        currentGloss = null,
                        confidence = null,
                        hasHands = frame.hasHands,
                        poseLandmarkCount = frame.pose.landmarks.size,
                        leftHandLandmarkCount = frame.leftHand.landmarks.size,
                        rightHandLandmarkCount = frame.rightHand.landmarks.size,
                        lipLandmarkCount = frame.lips.landmarks.size,
                        sequenceFrameCount = segmentFrames.size,
                        sequenceHandFrameCount = segmentFrames.count { it.hasHands },
                        sequenceTimestampsMs = segmentFrames.map { it.timestampMs },
                        featureProbe = feature.values.toFeatureProbeSlices(),
                        config = config,
                    ),
            ),
        )
        emitLiveWindowInferenceResult(frame)
    }

    private fun emitLiveWindowInferenceResult(frame: LandmarkFrameResult) {
        val snapshot = sequenceBuffer.buildSequenceSnapshot() ?: return
        logger.logModelInputStats(
            frameCount = sequenceBuffer.size,
            handFrameCount = sequenceBuffer.handFrameCount,
            sequence = snapshot.values,
        )
        var result: SignInferenceResult? = null
        val elapsedNanos =
            measureNanoTime {
                result = inferenceAdapter.predict(snapshot.values)
            }
        val inferenceResult = result ?: return
        logger.logInferenceResult(
            sequenceSize = snapshot.values.size,
            gloss = inferenceResult.gloss,
            confidence = inferenceResult.confidence,
            margin = inferenceResult.margin,
            secondGloss = inferenceResult.secondGloss,
            secondConfidence = inferenceResult.secondConfidence,
            rawGloss = inferenceResult.rawGloss,
            accepted = inferenceResult.accepted,
            rejectionReason = inferenceResult.rejectionReason,
            topCandidates = inferenceResult.topCandidates,
        )
        _events.tryEmit(
            SignRecognitionEvent.Metrics(
                snapshot =
                    SignRecognitionMetrics(
                        timestampMs = frame.timestampMs,
                        currentGloss =
                            inferenceResult.gloss.takeUnless {
                                it == SignModelContract.UNKNOWN_GLOSS
                            },
                        confidence = inferenceResult.confidence,
                        classIndex = inferenceResult.classIndex,
                        secondGloss = inferenceResult.secondGloss,
                        secondConfidence = inferenceResult.secondConfidence,
                        margin = inferenceResult.margin,
                        hasHands = frame.hasHands,
                        poseLandmarkCount = frame.pose.landmarks.size,
                        leftHandLandmarkCount = frame.leftHand.landmarks.size,
                        rightHandLandmarkCount = frame.rightHand.landmarks.size,
                        lipLandmarkCount = frame.lips.landmarks.size,
                        sequenceFrameCount = sequenceBuffer.size,
                        sequenceHandFrameCount = sequenceBuffer.handFrameCount,
                        sequenceTimestampsMs = snapshot.timestampsMs,
                        tfliteInferenceMs = elapsedNanos / NANOS_PER_MILLIS,
                        config = config,
                    ),
            ),
        )

        val stablePrediction =
            predictionStabilizer.onPrediction(
                result = inferenceResult,
                timestampMs = frame.timestampMs,
            ) ?: return
        logger.logPredictionEmitted(
            gloss = stablePrediction.gloss,
            confidence = stablePrediction.confidence,
        )
        appendSentencePrediction(stablePrediction)
        _events.tryEmit(
            SignRecognitionEvent.Prediction(
                gloss = stablePrediction.gloss,
                confidence = stablePrediction.confidence,
                timestampMs = frame.timestampMs,
            ),
        )
    }

    private fun appendSentencePrediction(prediction: StableSignPrediction) {
        if (sentenceBuffer.lastOrNull()?.gloss != prediction.gloss) {
            sentenceBuffer += prediction
        }
    }

    private fun updateSentenceType(frame: LandmarkFrameResult) {
        val currentGap = frame.normalizedEyebrowGap() ?: return
        if (neutralEyebrowGaps.size < MIN_NEUTRAL_FACE_SAMPLES) {
            return
        }
        val neutralGap = neutralEyebrowGaps.toList().median()
        if (currentGap - neutralGap >= QUESTION_EYEBROW_DELTA_THRESHOLD) {
            sentenceIsQuestion = true
        }
    }

    private fun addNeutralEyebrowGap(gap: Float) {
        if (neutralEyebrowGaps.size == NEUTRAL_FACE_WINDOW) {
            neutralEyebrowGaps.removeFirst()
        }
        neutralEyebrowGaps.addLast(gap)
    }

    private fun processNoHandsFrame(frame: LandmarkFrameResult) {
        if (!hasSeenHandsInSegment) {
            noHandsDetectionTracker.reset()
            return
        }

        missingHandFrames += 1
        if (missingHandFrames <= MAX_MISSING_HAND_FRAMES) {
            processFeatureFrame(frame)
            return
        }

        emitFinalSentence(frame)
        resetRecognitionState()
        logger.logNoHandsDetected()
        _events.tryEmit(SignRecognitionEvent.NoHandsDetected)
    }

    private fun emitFinalSentence(frame: LandmarkFrameResult) {
        if (sentenceBuffer.isEmpty()) {
            return
        }

        val glosses = sentenceBuffer.map { prediction -> prediction.gloss }
        val confidence =
            sentenceBuffer
                .map { prediction -> prediction.confidence }
                .average()
                .takeIf { value -> !value.isNaN() }
                ?.toFloat()
                ?: 0f
        val sentenceType =
            if (sentenceIsQuestion) {
                QUESTION_SENTENCE_TYPE
            } else {
                DECLARATIVE_SENTENCE_TYPE
            }
        _events.tryEmit(
            SignRecognitionEvent.Utterance(
                glosses = glosses,
                confidence = confidence,
                startedAtMs = segmentFrames.firstOrNull()?.timestampMs ?: frame.timestampMs,
                endedAtMs = segmentFrames.lastOrNull()?.timestampMs ?: frame.timestampMs,
                sentenceType = sentenceType,
            ),
        )
    }

    fun close() {
        stop()
        wordSpottingScanner.close()
        inferenceAdapter.close()
    }

    private fun resetSessionState() {
        noHandsDetectionTracker.reset()
        resetRecognitionState()
    }

    private fun resetRecognitionState() {
        hasSeenHandsInSegment = false
        missingHandFrames = 0
        sentenceIsQuestion = false
        sentenceBuffer.clear()
        featureEncoder.reset()
        segmentFrames.clear()
        sequenceBuffer.clear()
        predictionStabilizer.reset()
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64
        const val NANOS_PER_MILLIS = 1_000_000.0
        const val MAX_MISSING_HAND_FRAMES = 15
        const val NEUTRAL_FACE_WINDOW = 45
        const val MIN_NEUTRAL_FACE_SAMPLES = 15
        const val QUESTION_EYEBROW_DELTA_THRESHOLD = 0.055f
        const val DECLARATIVE_SENTENCE_TYPE = "평서문"
        const val QUESTION_SENTENCE_TYPE = "의문문"
    }
}

private fun LandmarkFrameResult.normalizedEyebrowGap(): Float? {
    if (!face.landmarks.hasIndices(EYEBROW_FACE_INDICES)) {
        return null
    }
    val leftEye = face.landmarks.averagePoint(LEFT_EYE_CENTER_INDICES)
    val rightEye = face.landmarks.averagePoint(RIGHT_EYE_CENTER_INDICES)
    val eyeDistance = leftEye.distanceTo2d(rightEye)
    if (eyeDistance < MIN_EYE_DISTANCE) {
        return null
    }

    val eyebrowY =
        (LEFT_EYEBROW_INDICES + RIGHT_EYEBROW_INDICES)
            .map { index -> face.landmarks[index].y }
            .average()
            .toFloat()
    val eyeY =
        (LEFT_EYE_CENTER_INDICES + RIGHT_EYE_CENTER_INDICES)
            .map { index -> face.landmarks[index].y }
            .average()
            .toFloat()
    return (eyeY - eyebrowY) / eyeDistance
}

private fun List<LandmarkPoint>.averagePoint(indices: List<Int>): LandmarkPoint {
    val points = indices.map { index -> this[index] }
    return LandmarkPoint(
        x = points.map { point -> point.x }.average().toFloat(),
        y = points.map { point -> point.y }.average().toFloat(),
        z = points.map { point -> point.z }.average().toFloat(),
    )
}

private fun LandmarkPoint.distanceTo2d(other: LandmarkPoint): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun List<Float>.median(): Float {
    if (isEmpty()) {
        return 0f
    }
    val sortedValues = sorted()
    val middle = sortedValues.size / 2
    return if (sortedValues.size % 2 == 0) {
        (sortedValues[middle - 1] + sortedValues[middle]) / 2f
    } else {
        sortedValues[middle]
    }
}

private fun List<LandmarkPoint>.hasIndices(indices: List<Int>): Boolean =
    indices.all { index -> index in this.indices }

private fun FloatArray.toFeatureProbeSlices(): SignFeatureProbeSlices =
    SignFeatureProbeSlices(
        head = sliceFrom(startIndex = 0),
        faceStart = sliceFrom(startIndex = SignFeatureSpec.FACE_OFFSET),
        poseStart = sliceFrom(startIndex = SignFeatureSpec.POSE_OFFSET),
        distanceStart = sliceFrom(startIndex = SignFeatureSpec.DISTANCE_OFFSET),
    )

private fun FloatArray.sliceFrom(startIndex: Int): List<Float> =
    copyOfRange(
        fromIndex = startIndex,
        toIndex =
            (startIndex + FEATURE_PROBE_SLICE_SIZE)
                .coerceAtMost(size),
    ).toList()

private const val FEATURE_PROBE_SLICE_SIZE = 10
private val LEFT_EYEBROW_INDICES = listOf(70, 63, 105, 66)
private val RIGHT_EYEBROW_INDICES = listOf(336, 296, 334, 293)
private val LEFT_EYE_CENTER_INDICES = listOf(33, 133)
private val RIGHT_EYE_CENTER_INDICES = listOf(362, 263)
private val EYEBROW_FACE_INDICES =
    LEFT_EYEBROW_INDICES +
        RIGHT_EYEBROW_INDICES +
        LEFT_EYE_CENTER_INDICES +
        RIGHT_EYE_CENTER_INDICES
private const val MIN_EYE_DISTANCE = 0.000001f
