package com.ssafy.mobile.core.vision.inference

import android.content.Context
import java.io.IOException

class SignInferenceAdapterFactory(
    private val context: Context,
    private val modelAssetPath: String = SignModelContract.MODEL_ASSET_PATH,
    private val labelMapAssetPath: String = SignModelContract.LABEL_MAP_ASSET_PATH,
) {
    fun create(): SignInferenceAdapter {
        val appContext = context.applicationContext
        val assetState =
            SignInferenceAssetState(
                hasModel = appContext.hasAsset(modelAssetPath),
                hasLabelMap = appContext.hasAsset(labelMapAssetPath),
            )

        return when (assetState.resolvePolicy()) {
            SignInferenceAdapterPolicy.FAKE ->
                FakeSignInferenceAdapter()
            SignInferenceAdapterPolicy.TFLITE ->
                TfliteSignInferenceAdapter.create(
                    context = appContext,
                    modelAssetPath = modelAssetPath,
                    labelMapAssetPath = labelMapAssetPath,
                )
        }
    }
}

data class SignInferenceAssetState(
    val hasModel: Boolean,
    val hasLabelMap: Boolean,
)

enum class SignInferenceAdapterPolicy {
    FAKE,
    TFLITE,
}

fun SignInferenceAssetState.resolvePolicy(): SignInferenceAdapterPolicy =
    when {
        !hasModel && !hasLabelMap -> SignInferenceAdapterPolicy.FAKE
        hasModel && hasLabelMap -> SignInferenceAdapterPolicy.TFLITE
        else -> error("TFLite model and label map assets must be provided together.")
    }

private fun Context.hasAsset(assetPath: String): Boolean =
    try {
        assets.open(assetPath).use { true }
    } catch (_: IOException) {
        false
    }
