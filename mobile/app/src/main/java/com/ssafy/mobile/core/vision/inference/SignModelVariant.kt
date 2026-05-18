package com.ssafy.mobile.core.vision.inference

enum class SignModelVariant(
    val displayName: String,
    val modelAssetPath: String,
) {
    FLOAT16(
        displayName = "v6-float16",
        modelAssetPath = "models/sign_model_v6_float16.tflite",
    ),
    FLOAT32(
        displayName = "v6-float32",
        modelAssetPath = "models/sign_model_v6_float32.tflite",
    ),
    V7_FLOAT16(
        displayName = "v7-117words-float16",
        modelAssetPath = "models/best_sign_model_v7_float16.tflite",
    ),
    ;

    companion object {
        val DEFAULT = V7_FLOAT16
    }
}
