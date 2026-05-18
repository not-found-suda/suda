package com.ssafy.mobile.feature.sign.presentation

import android.content.Context
import android.util.Log
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureEncoder
import com.ssafy.mobile.core.vision.feature.LandmarkFeatureSequenceResampler
import com.ssafy.mobile.core.vision.inference.DebugReplayInferenceMode
import com.ssafy.mobile.core.vision.inference.SignInferenceAdapterFactory
import com.ssafy.mobile.core.vision.inference.SignInferenceResult
import com.ssafy.mobile.core.vision.inference.SignInferenceRuntimeConfig
import com.ssafy.mobile.core.vision.inference.SignInferenceRuntimeMode
import com.ssafy.mobile.core.vision.inference.resolve
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult

internal class WholeVideoIntentClassifier(
    private val context: Context,
) {
    fun classify(frames: List<LandmarkFrameResult>): SignInferenceResult {
        require(frames.isNotEmpty()) {
            "Video replay did not produce any landmark frames."
        }

        val replayMode = SignInferenceRuntimeConfig.debugReplayMode.resolve()
        Log.d(TAG, "Whole-video replay inference mode=$replayMode")

        return when (replayMode) {
            DebugReplayInferenceMode.TFLITE_INTENT_CLASSIFIER ->
                classifyWithTfliteIntentClassifier(frames)
            DebugReplayInferenceMode.MATCH_PRIMARY ->
                error("Replay inference mode must be resolved.")
        }
    }

    private fun classifyWithTfliteIntentClassifier(
        frames: List<LandmarkFrameResult>,
    ): SignInferenceResult {
        val encoder = LandmarkFeatureEncoder()
        val featureFrames = frames.map(encoder::encode)
        val sequence = LandmarkFeatureSequenceResampler.resampleToModelInput(featureFrames)
        val adapter =
            SignInferenceAdapterFactory(
                context = context,
                runtimeMode = SignInferenceRuntimeMode.TFLITE,
            ).create()

        return try {
            val result = adapter.predict(sequence)
            result.copy(gloss = "TFLite: ${result.gloss}")
        } finally {
            adapter.close()
        }
    }

    private companion object {
        const val TAG = "SignReplay"
    }
}
