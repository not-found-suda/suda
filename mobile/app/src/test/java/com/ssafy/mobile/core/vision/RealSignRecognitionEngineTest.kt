package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureEncoder
import com.ssafy.mobile.core.vision.feature.SignFeatureSpec
import com.ssafy.mobile.core.vision.feature.SignSequenceBuffer
import com.ssafy.mobile.core.vision.inference.SignInferenceAdapter
import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.landmark.HandLandmarks
import com.ssafy.mobile.core.vision.landmark.HandSide
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.LandmarkGroup
import com.ssafy.mobile.core.vision.landmark.LandmarkGroupType
import com.ssafy.mobile.core.vision.landmark.LandmarkPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealSignRecognitionEngineTest {
    @Test
    fun emitsStartedAndReadyWhenStarted() =
        runBlocking {
            val engine = createEngine()
            val eventsDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events.take(2).toList()
                    }
                }

            yield()
            engine.start()

            val events = eventsDeferred.await()
            assertEquals(SignRecognitionEvent.Started, events[0])
            assertEquals(SignRecognitionEvent.Ready, events[1])
        }

    @Test
    fun ignoresDuplicatedStartCalls() =
        runBlocking {
            val engine = createEngine()
            val eventsDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events.take(2).toList()
                    }
                }

            yield()
            engine.start()
            engine.start()

            val events = eventsDeferred.await()
            assertEquals(listOf(SignRecognitionEvent.Started, SignRecognitionEvent.Ready), events)
        }

    @Test
    fun emitsPredictionWhenLiveWindowIsStable() =
        runBlocking {
            val engine = createEngine()
            val predictionDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events.filterIsInstance<SignRecognitionEvent.Prediction>().first()
                    }
                }

            yield()
            engine.start()
            repeatStablePredictionFrames(startTimestampMs = 1L, engine = engine)
            engine.submitFrame(createNoHandsFrame(timestampMs = 1_000L))
            engine.submitFrame(createNoHandsFrame(timestampMs = 1_000L + NO_HANDS_DELAY_MS))

            val prediction = predictionDeferred.await()
            assertEquals(TEST_GLOSS, prediction.gloss)
            assertEquals(TEST_CONFIDENCE, prediction.confidence, FLOAT_DELTA)
            assertEquals(151L, prediction.timestampMs)
        }

    @Test
    fun emitsPredictionWithResampledPartialSegment() =
        runBlocking {
            val inferenceAdapter = RecordingInferenceAdapter()
            val engine =
                createEngine(
                    inferenceAdapter = inferenceAdapter,
                    sequenceBuffer =
                        SignSequenceBuffer(
                            sequenceLength = TEST_PARTIAL_SEQUENCE_LENGTH,
                            minimumPredictionFrames = TEST_MINIMUM_PREDICTION_FRAMES,
                        ),
                    predictionStabilizer =
                        SignPredictionStabilizer(
                            confidenceThreshold = TEST_CONFIDENCE_THRESHOLD,
                            windowSize = 1,
                            requiredVotes = 1,
                        ),
                )
            val predictionDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events.filterIsInstance<SignRecognitionEvent.Prediction>().first()
                    }
                }

            yield()
            engine.start()
            repeatStablePredictionFrames(startTimestampMs = 1L, engine = engine)
            engine.submitFrame(createNoHandsFrame(timestampMs = 1_000L))
            engine.submitFrame(createNoHandsFrame(timestampMs = 1_000L + NO_HANDS_DELAY_MS))

            assertTrue(inferenceAdapter.predictCallCount > 0)
            assertEquals(TEST_GLOSS, predictionDeferred.await().gloss)
        }

    @Test
    fun waitsForMinimumLiveWindowBeforeRunningInference() =
        runBlocking {
            val inferenceAdapter = RecordingInferenceAdapter()
            val engine =
                createEngine(
                    inferenceAdapter = inferenceAdapter,
                    predictionStabilizer =
                        SignPredictionStabilizer(
                            confidenceThreshold = TEST_CONFIDENCE_THRESHOLD,
                            windowSize = TEST_PREDICTION_WINDOW_SIZE,
                            requiredVotes = TEST_PREDICTION_WINDOW_SIZE,
                        ),
                )

            engine.start()
            engine.submitFrame(createFrame(timestampMs = 1L))
            assertEquals(0, inferenceAdapter.predictCallCount)

            engine.submitFrame(createFrame(timestampMs = 2L))
            assertEquals(1, inferenceAdapter.predictCallCount)

            engine.submitFrame(createFrame(timestampMs = 3L))
            assertEquals(2, inferenceAdapter.predictCallCount)
        }

    @Test
    fun ignoresFramesWhenStopped() =
        runBlocking {
            val inferenceAdapter = RecordingInferenceAdapter()
            val engine = createEngine(inferenceAdapter = inferenceAdapter)

            engine.submitFrame(createFrame(timestampMs = 1L))

            assertEquals(0, inferenceAdapter.predictCallCount)
        }

    @Test
    fun emitsStoppedAndClearsSessionState() =
        runBlocking {
            val inferenceAdapter = RecordingInferenceAdapter()
            val engine = createEngine(inferenceAdapter = inferenceAdapter)
            val stoppedDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events.filterIsInstance<SignRecognitionEvent.Stopped>().first()
                    }
                }

            yield()
            engine.start()
            engine.submitFrame(createFrame(timestampMs = 1L))
            engine.stop()
            engine.start()
            engine.submitFrame(createFrame(timestampMs = 2L))

            assertEquals(SignRecognitionEvent.Stopped, stoppedDeferred.await())
            assertEquals(0, inferenceAdapter.predictCallCount)
        }

    @Test
    fun emitsErrorWhenInferenceFails() =
        runBlocking {
            val engine =
                createEngine(
                    inferenceAdapter =
                        RecordingInferenceAdapter(
                            shouldThrow = true,
                        ),
                )
            val errorDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events.filterIsInstance<SignRecognitionEvent.Error>().first()
                    }
                }

            yield()
            engine.start()
            repeatStablePredictionFrames(startTimestampMs = 1L, engine = engine)
            engine.submitFrame(createNoHandsFrame(timestampMs = 1_000L))
            engine.submitFrame(createNoHandsFrame(timestampMs = 1_000L + NO_HANDS_DELAY_MS))

            val error = errorDeferred.await()
            assertTrue(error.cause is IllegalStateException)
        }

    @Test
    fun emitsNoHandsDetectedWhenHandsDisappear() =
        runBlocking {
            val engine = createEngine()
            val noHandsDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events.first { event ->
                            event == SignRecognitionEvent.NoHandsDetected
                        }
                    }
                }

            yield()
            engine.start()
            engine.submitFrame(createFrame(timestampMs = 0L))
            submitMissingHandFrames(engine = engine, startTimestampMs = 10L)

            assertEquals(SignRecognitionEvent.NoHandsDetected, noHandsDeferred.await())
        }

    @Test
    fun startsNewSegmentAfterHandsDisappear() =
        runBlocking {
            val inferenceAdapter = RecordingInferenceAdapter()
            val engine = createEngine(inferenceAdapter = inferenceAdapter)

            engine.start()
            engine.submitFrame(createFrame(timestampMs = 1L))
            submitMissingHandFrames(engine = engine, startTimestampMs = 2L)
            val predictCountAfterReset = inferenceAdapter.predictCallCount

            engine.submitFrame(createFrame(timestampMs = 3L + NO_HANDS_DELAY_MS))
            assertEquals(predictCountAfterReset, inferenceAdapter.predictCallCount)

            repeatStablePredictionFrames(startTimestampMs = 4L + NO_HANDS_DELAY_MS, engine = engine)

            assertTrue(inferenceAdapter.predictCallCount > predictCountAfterReset)
        }

    @Test
    fun allowsSameGlossAfterHandsAreDetectedAgain() =
        runBlocking {
            val engine = createEngine()
            val predictionsDeferred =
                async {
                    withTimeout(TIMEOUT_MS) {
                        engine.events
                            .filterIsInstance<SignRecognitionEvent.Prediction>()
                            .take(2)
                            .toList()
                    }
                }

            yield()
            engine.start()
            repeatStablePredictionFrames(startTimestampMs = 1L, engine = engine)
            submitMissingHandFrames(engine = engine, startTimestampMs = 1_000L)
            repeatStablePredictionFrames(startTimestampMs = 2_000L, engine = engine)
            submitMissingHandFrames(engine = engine, startTimestampMs = 3_000L)

            val predictions = predictionsDeferred.await()
            assertEquals(TEST_GLOSS, predictions[0].gloss)
            assertEquals(TEST_GLOSS, predictions[1].gloss)
        }

    private fun createEngine(
        inferenceAdapter: SignInferenceAdapter = RecordingInferenceAdapter(),
        sequenceBuffer: SignSequenceBuffer =
            SignSequenceBuffer(
                sequenceLength = TEST_SEQUENCE_LENGTH,
            ),
        predictionStabilizer: SignPredictionStabilizer =
            SignPredictionStabilizer(
                confidenceThreshold = TEST_CONFIDENCE_THRESHOLD,
                windowSize = TEST_PREDICTION_WINDOW_SIZE,
                requiredVotes = TEST_REQUIRED_VOTES,
            ),
    ): RealSignRecognitionEngine =
        RealSignRecognitionEngine(
            featureEncoder = LandmarkFeatureEncoder(),
            sequenceBuffer = sequenceBuffer,
            inferenceAdapter = inferenceAdapter,
            noHandsDetectionTracker =
                NoHandsDetectionTracker(
                    detectionDelayMs = NO_HANDS_DELAY_MS,
                ),
            logger =
                SignPipelineLogger(
                    log = {},
                    elapsedRealtime = { 0L },
                ),
            predictionStabilizer = predictionStabilizer,
        )

    private class RecordingInferenceAdapter(
        private val shouldThrow: Boolean = false,
    ) : SignInferenceAdapter {
        var predictCallCount = 0
            private set

        override fun predict(sequence: FloatArray): SignInferenceResult {
            predictCallCount += 1
            check(!shouldThrow) { "테스트용 추론 실패" }
            return SignInferenceResult(
                gloss = TEST_GLOSS,
                confidence = TEST_CONFIDENCE,
            )
        }

        override fun close() = Unit
    }

    private fun createFrame(timestampMs: Long): LandmarkFrameResult =
        LandmarkFrameResult(
            timestampMs = timestampMs,
            pose =
                LandmarkGroup(
                    type = LandmarkGroupType.POSE,
                    landmarks = createPoseLandmarks(),
                ),
            leftHand =
                HandLandmarks(
                    side = HandSide.LEFT,
                    landmarks = createLandmarks(SignFeatureSpec.HAND_LANDMARK_COUNT),
                ),
            rightHand =
                HandLandmarks(
                    side = HandSide.RIGHT,
                    landmarks = createLandmarks(SignFeatureSpec.HAND_LANDMARK_COUNT),
                ),
            lips =
                LandmarkGroup(
                    type = LandmarkGroupType.LIPS,
                    landmarks = emptyList(),
                ),
        )

    private fun repeatStablePredictionFrames(
        startTimestampMs: Long,
        engine: RealSignRecognitionEngine,
    ) {
        repeat(TEST_STABLE_SEGMENT_FRAMES) { index ->
            engine.submitFrame(
                createFrame(timestampMs = startTimestampMs + index * TEST_FRAME_INTERVAL_MS),
            )
        }
    }

    private fun submitMissingHandFrames(
        engine: RealSignRecognitionEngine,
        startTimestampMs: Long,
    ) {
        repeat(MISSING_HAND_RESET_FRAMES) { index ->
            engine.submitFrame(
                createNoHandsFrame(timestampMs = startTimestampMs + index * TEST_FRAME_INTERVAL_MS),
            )
        }
    }

    private fun createNoHandsFrame(timestampMs: Long): LandmarkFrameResult =
        LandmarkFrameResult(
            timestampMs = timestampMs,
            pose =
                LandmarkGroup(
                    type = LandmarkGroupType.POSE,
                    landmarks = createPoseLandmarks(),
                ),
            leftHand = HandLandmarks.empty(HandSide.LEFT),
            rightHand = HandLandmarks.empty(HandSide.RIGHT),
            lips =
                LandmarkGroup(
                    type = LandmarkGroupType.LIPS,
                    landmarks = emptyList(),
                ),
        )

    private fun createPoseLandmarks(): List<LandmarkPoint> =
        createLandmarks(FULL_POSE_LANDMARK_COUNT).toMutableList().also { landmarks ->
            landmarks[SignFeatureSpec.LEFT_SHOULDER_INDEX] = LandmarkPoint(0f, 0f, 0f)
            landmarks[SignFeatureSpec.RIGHT_SHOULDER_INDEX] = LandmarkPoint(1f, 0f, 0f)
        }

    private fun createLandmarks(count: Int): List<LandmarkPoint> =
        List(count) { index ->
            LandmarkPoint(
                x = index.toFloat() / count,
                y = 0f,
                z = 0f,
            )
        }

    private companion object {
        const val TEST_SEQUENCE_LENGTH = 2
        const val TEST_PARTIAL_SEQUENCE_LENGTH = 4
        const val TEST_MINIMUM_PREDICTION_FRAMES = 2
        const val TEST_GLOSS = "엄마"
        const val TEST_CONFIDENCE = 0.91f
        const val TEST_CONFIDENCE_THRESHOLD = 0.85f
        const val TEST_PREDICTION_WINDOW_SIZE = 5
        const val TEST_REQUIRED_VOTES = 3
        const val TEST_STABLE_SEGMENT_FRAMES = 8
        const val TEST_FRAME_INTERVAL_MS = 50L
        const val NO_HANDS_DELAY_MS = 1_000L
        const val MISSING_HAND_RESET_FRAMES = 22
        const val FULL_POSE_LANDMARK_COUNT = 33
        const val FLOAT_DELTA = 0.0001f
        const val TIMEOUT_MS = 1_000L
    }
}
