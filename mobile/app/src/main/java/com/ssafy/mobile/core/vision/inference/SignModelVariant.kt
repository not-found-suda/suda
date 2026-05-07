package com.ssafy.mobile.core.vision.inference

enum class SignModelVariant(
    val displayName: String,
    val modelAssetPath: String,
) {
    FLOAT16(
        displayName = "float16",
        modelAssetPath = "models/sign_model_v5_1_float16.tflite",
    ),
    FLOAT32(
        displayName = "float32",
        modelAssetPath = "models/sign_model_v5_1_float32.tflite",
    ),
    ;

    companion object {
        val DEFAULT = FLOAT16
    }
}
