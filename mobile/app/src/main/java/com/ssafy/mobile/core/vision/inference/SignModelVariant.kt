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
    ;

    companion object {
        val DEFAULT = FLOAT32
    }
}
