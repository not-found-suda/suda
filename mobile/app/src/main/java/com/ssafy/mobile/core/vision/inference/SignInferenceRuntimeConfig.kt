package com.ssafy.mobile.core.vision.inference

enum class SignInferenceRuntimeMode {
    TFLITE,
}

enum class DebugReplayInferenceMode {
    MATCH_PRIMARY,
    TFLITE_INTENT_CLASSIFIER,
}

object SignInferenceRuntimeConfig {
    val primaryMode: SignInferenceRuntimeMode = SignInferenceRuntimeMode.TFLITE
    val debugReplayMode: DebugReplayInferenceMode = DebugReplayInferenceMode.MATCH_PRIMARY
}

fun DebugReplayInferenceMode.resolve(): DebugReplayInferenceMode =
    when (this) {
        DebugReplayInferenceMode.MATCH_PRIMARY ->
            when (SignInferenceRuntimeConfig.primaryMode) {
                SignInferenceRuntimeMode.TFLITE ->
                    DebugReplayInferenceMode.TFLITE_INTENT_CLASSIFIER
            }
        else -> this
    }
