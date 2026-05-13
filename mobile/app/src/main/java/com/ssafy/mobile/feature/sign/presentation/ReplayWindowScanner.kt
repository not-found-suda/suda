package com.ssafy.mobile.feature.sign.presentation

import android.content.Context
import android.util.Log
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureEncoder
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureFrame
import com.ssafy.mobile.core.vision.inference.SignInferenceAdapterFactory
import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.inference.SignInferenceRuntimeMode
import com.ssafy.mobile.core.vision.inference.SignModelContract
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import java.util.Locale

internal class ReplayWindowScanner(
    private val context: Context,
) {
    fun logExhaustiveWindows(frames: List<LandmarkFrameResult>) {
        if (!LOG_EXHAUSTIVE_WINDOWS) return

        if (frames.size < SignModelContract.SEQUENCE_LENGTH) {
            Log.d(
                TAG,
                "SKIP frameCount=${frames.size} required=${SignModelContract.SEQUENCE_LENGTH}",
            )
            return
        }

        val features = frames.map(LandmarkFeatureEncoder()::encode)
        val bestHits: MutableMap<String, ReplayWindowTargetHit?> =
            TARGET_GLOSSES
                .associateWith { null as ReplayWindowTargetHit? }
                .toMutableMap()
        val inferenceAdapter =
            SignInferenceAdapterFactory(
                context = context,
                runtimeMode = SignInferenceRuntimeMode.TFLITE,
            ).create()

        try {
            val lastWindowStart = features.size - SignModelContract.SEQUENCE_LENGTH
            Log.d(TAG, "START frameCount=${features.size} windows=${lastWindowStart + 1}")
            for (windowStart in 0..lastWindowStart) {
                val windowEnd = windowStart + SignModelContract.SEQUENCE_LENGTH
                val window = features.subList(windowStart, windowEnd)
                val hit =
                    ReplayWindowHit(
                        windowIndex = windowStart,
                        startFrameNumber = windowStart + 1,
                        endFrameNumber = windowEnd,
                        startTimestampMs = window.first().timestampMs,
                        endTimestampMs = window.last().timestampMs,
                        handFrameCount = window.count { it.hasHands },
                        result = inferenceAdapter.predict(window.toSequenceInput()),
                    )

                logWindow(hit)
                updateBestHits(bestHits, hit)
            }
            logSummary(bestHits)
        } finally {
            inferenceAdapter.close()
        }
    }

    private fun List<LandmarkFeatureFrame>.toSequenceInput(): FloatArray {
        val sequence =
            FloatArray(
                SignModelContract.SEQUENCE_LENGTH *
                    SignModelContract.FEATURE_DIMENSION,
            )
        var offset = 0
        forEach { frame ->
            frame.values.copyInto(
                destination = sequence,
                destinationOffset = offset,
            )
            offset += SignModelContract.FEATURE_DIMENSION
        }
        return sequence
    }

    private fun logWindow(hit: ReplayWindowHit) {
        val result = hit.result
        val top1Index = result.classIndex?.toString() ?: "-"
        Log.d(
            TAG,
            "WINDOW #${hit.windowIndex} " +
                "frames=${hit.startFrameNumber}-${hit.endFrameNumber} " +
                "time=${hit.startTimestampMs}-${hit.endTimestampMs}ms " +
                "hands=${hit.handFrameCount}/${SignModelContract.SEQUENCE_LENGTH} " +
                "top1=[$top1Index]${result.gloss} " +
                formatPercent(result.confidence) +
                " top2=${result.secondGloss ?: "-"} " +
                formatPercent(result.secondConfidence ?: 0f) +
                " margin=${formatPercent(result.margin)}",
        )
    }

    private fun updateBestHits(
        bestHits: MutableMap<String, ReplayWindowTargetHit?>,
        hit: ReplayWindowHit,
    ) {
        val result = hit.result
        bestHits.updateBestHit(
            gloss = result.gloss,
            candidate =
                ReplayWindowTargetHit(
                    hit = hit,
                    rank = 1,
                    confidence = result.confidence,
                ),
        )
        result.secondGloss?.let { secondGloss ->
            bestHits.updateBestHit(
                gloss = secondGloss,
                candidate =
                    ReplayWindowTargetHit(
                        hit = hit,
                        rank = 2,
                        confidence = result.secondConfidence ?: 0f,
                    ),
            )
        }
    }

    private fun MutableMap<String, ReplayWindowTargetHit?>.updateBestHit(
        gloss: String,
        candidate: ReplayWindowTargetHit,
    ) {
        if (!containsKey(gloss)) return

        val current = this[gloss]
        if (current == null || candidate.confidence > current.confidence) {
            this[gloss] = candidate
        }
    }

    private fun logSummary(bestHits: Map<String, ReplayWindowTargetHit?>) {
        bestHits.forEach { (gloss, bestHit) ->
            if (bestHit == null) {
                Log.d(TAG, "BEST $gloss none")
                return@forEach
            }

            val hit = bestHit.hit
            Log.d(
                TAG,
                "BEST $gloss " +
                    "rank=top${bestHit.rank} " +
                    "confidence=${formatPercent(bestHit.confidence)} " +
                    "window=#${hit.windowIndex} " +
                    "frames=${hit.startFrameNumber}-${hit.endFrameNumber} " +
                    "time=${hit.startTimestampMs}-${hit.endTimestampMs}ms " +
                    "windowTop1=${hit.result.gloss} " +
                    formatPercent(hit.result.confidence),
            )
        }
    }

    private fun formatPercent(value: Float): String =
        String.format(Locale.US, "%.1f%%", value * PERCENT_MULTIPLIER)

    private data class ReplayWindowHit(
        val windowIndex: Int,
        val startFrameNumber: Int,
        val endFrameNumber: Int,
        val startTimestampMs: Long,
        val endTimestampMs: Long,
        val handFrameCount: Int,
        val result: SignInferenceResult,
    )

    private data class ReplayWindowTargetHit(
        val hit: ReplayWindowHit,
        val rank: Int,
        val confidence: Float,
    )

    private companion object {
        const val LOG_EXHAUSTIVE_WINDOWS = true
        const val PERCENT_MULTIPLIER = 100f
        const val TAG = "SignReplayWindow"
        val TARGET_GLOSSES =
            listOf(
                "장난감",
                "조심",
                "일어나다",
                "기차",
                "병원",
                "가다",
            )
    }
}
