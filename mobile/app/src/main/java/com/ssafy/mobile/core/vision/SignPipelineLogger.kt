package com.ssafy.mobile.core.vision

import android.os.SystemClock
import android.util.Log
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureProbe
import com.ssafy.mobile.core.vision.feature.SignSequenceBuffer
import com.ssafy.mobile.core.vision.inference.SignInferenceCandidate
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import com.ssafy.mobile.core.vision.landmark.LandmarkPoint
import java.util.Locale

@Suppress("TooManyFunctions")
class SignPipelineLogger(
    private val log: (String) -> Unit = { message -> Log.d(TAG, message) },
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private var lastFrameLogAtMillis = 0L
    private var lastInferenceLogAtMillis = 0L
    private var lastFeatureProbeLogAtMillis = 0L

    fun logEngineStarted() {
        log("Sign recognition engine started.")
    }

    fun logInferenceAdapter(
        adapterName: String,
        supportsFullSegmentInference: Boolean,
    ) {
        log(
            "Inference adapter. name=$adapterName, " +
                "supportsFullSegmentInference=$supportsFullSegmentInference",
        )
    }

    fun logHandForwardFillMode(isEnabled: Boolean) {
        log("Hand forward-fill. enabled=$isEnabled")
    }

    fun logEngineStopped() {
        log("Sign recognition engine stopped.")
    }

    fun logFrameState(frame: LandmarkFrameResult) {
        val now = elapsedRealtime()
        if (now - lastFrameLogAtMillis < DEBUG_LOG_INTERVAL_MILLIS) {
            return
        }
        lastFrameLogAtMillis = now

        log(
            "Landmark frame. pose=${frame.pose.landmarks.size}, " +
                "left=${frame.leftHand.landmarks.size}, " +
                "right=${frame.rightHand.landmarks.size}, " +
                "lips=${frame.lips.landmarks.size}, " +
                "hasHands=${frame.hasHands}",
        )
    }

    fun logSequenceBuffering(size: Int) {
        log("Sequence buffering. frames=$size/${SignSequenceBuffer.DEFAULT_SEQUENCE_LENGTH}")
    }

    fun logSequenceWaitingForHands(
        handFrames: Int,
        totalFrames: Int,
    ) {
        log(
            "Sequence waiting for hands. handFrames=$handFrames/$totalFrames",
        )
    }

    fun logFeatureProbe(probe: LandmarkFeatureProbe) {
        val now = elapsedRealtime()
        if (now - lastFeatureProbeLogAtMillis < DEBUG_LOG_INTERVAL_MILLIS) {
            return
        }
        lastFeatureProbeLogAtMillis = now

        log(
            "Feature probe RAW " +
                "LH0=${probe.raw.leftHandWrist.toLogString()}, " +
                "RH0=${probe.raw.rightHandWrist.toLogString()}, " +
                "NOSE=${probe.raw.nose.toLogString()}, " +
                "LS=${probe.raw.leftShoulder.toLogString()}, " +
                "RS=${probe.raw.rightShoulder.toLogString()}",
        )
        log(
            "Feature probe NORM " +
                "LH0=${probe.normalized.leftHandWrist.toLogString()}, " +
                "RH0=${probe.normalized.rightHandWrist.toLogString()}, " +
                "NOSE=${probe.normalized.nose.toLogString()}, " +
                "LS=${probe.normalized.leftShoulder.toLogString()}, " +
                "RS=${probe.normalized.rightShoulder.toLogString()}",
        )
    }

    @Suppress("LongParameterList")
    fun logInferenceResult(
        sequenceSize: Int,
        gloss: String,
        confidence: Float,
        margin: Float? = null,
        secondGloss: String? = null,
        secondConfidence: Float? = null,
        rawGloss: String? = null,
        accepted: Boolean? = null,
        rejectionReason: String? = null,
        topCandidates: List<SignInferenceCandidate> = emptyList(),
    ) {
        val now = elapsedRealtime()
        if (now - lastInferenceLogAtMillis < DEBUG_LOG_INTERVAL_MILLIS) {
            return
        }
        lastInferenceLogAtMillis = now

        log(
            "Inference result. sequenceSize=$sequenceSize, gloss=$gloss, " +
                "confidence=$confidence, margin=$margin, " +
                "secondGloss=$secondGloss, secondConfidence=$secondConfidence, " +
                "rawGloss=$rawGloss, accepted=$accepted, reason=$rejectionReason, " +
                "top=${topCandidates.toLogString()}",
        )
    }

    fun logModelInputStats(
        frameCount: Int,
        handFrameCount: Int,
        sequence: FloatArray,
    ) {
        val now = elapsedRealtime()
        if (now - lastInferenceLogAtMillis < DEBUG_LOG_INTERVAL_MILLIS) {
            return
        }

        val stats = sequence.toStats()
        log(
            "Model input. segmentFrames=$frameCount, handFrames=$handFrameCount, " +
                "flatSize=${sequence.size}, mean=${stats.mean.format()}, " +
                "std=${stats.std.format()}, zeroRatio=${stats.zeroRatio.format()}, " +
                "min=${stats.min.format()}, max=${stats.max.format()}",
        )
    }

    fun logPredictionEmitted(
        gloss: String,
        confidence: Float,
    ) {
        log("Prediction emitted. gloss=$gloss, confidence=$confidence")
    }

    fun logNoHandsDetected() {
        log("No hands detected. recognition state reset.")
    }

    private companion object {
        const val TAG = "SignPipeline"
        const val DEBUG_LOG_INTERVAL_MILLIS = 1_000L
    }
}

private fun List<SignInferenceCandidate>.toLogString(): String =
    joinToString(separator = " | ") { candidate ->
        "#${candidate.rank}:${candidate.gloss}[${candidate.classIndex}]=" +
            candidate.confidence.format()
    }

private fun FloatArray.toStats(): SequenceStats {
    if (isEmpty()) {
        return SequenceStats(
            mean = 0f,
            std = 0f,
            zeroRatio = 0f,
            min = 0f,
            max = 0f,
        )
    }

    var sum = 0.0
    var squareSum = 0.0
    var zeroCount = 0
    var min = Float.POSITIVE_INFINITY
    var max = Float.NEGATIVE_INFINITY
    forEach { value ->
        sum += value
        squareSum += value * value
        if (value == 0f) {
            zeroCount += 1
        }
        if (value < min) {
            min = value
        }
        if (value > max) {
            max = value
        }
    }
    val mean = (sum / size).toFloat()
    val variance = (squareSum / size - mean * mean).coerceAtLeast(0.0)
    return SequenceStats(
        mean = mean,
        std = kotlin.math.sqrt(variance).toFloat(),
        zeroRatio = zeroCount.toFloat() / size,
        min = min,
        max = max,
    )
}

private fun Float.format(): String = String.format(Locale.US, "%.4f", this)

private data class SequenceStats(
    val mean: Float,
    val std: Float,
    val zeroRatio: Float,
    val min: Float,
    val max: Float,
)

private fun LandmarkPoint.toLogString(): String =
    String.format(Locale.US, "(%.4f,%.4f,%.4f)", x, y, z)
