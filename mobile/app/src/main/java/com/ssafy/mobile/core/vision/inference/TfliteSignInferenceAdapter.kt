package com.ssafy.mobile.core.vision.inference

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

class TfliteSignInferenceAdapter private constructor(
    private val model: TfliteSignModel,
) : SignInferenceAdapter {
    private var isClosed = false

    init {
        model.interpreter.validateModelContract()
        Log.d(
            TAG,
            "TFLite contract verified. input=${SignModelContract.inputShape.contentToString()}, " +
                "output=${SignModelContract.outputShape.contentToString()}",
        )
    }

    override fun predict(sequence: FloatArray): SignInferenceResult {
        check(!isClosed) {
            "TFLite sign inference adapter is already closed."
        }
        require(sequence.size == SignModelContract.FLAT_SEQUENCE_INPUT_SIZE) {
            "Sequence input size must be ${SignModelContract.FLAT_SEQUENCE_INPUT_SIZE}."
        }

        val output =
            Array(SignModelContract.BATCH_SIZE) {
                FloatArray(SignModelContract.CLASS_COUNT)
            }
        model.interpreter.run(sequence.toInputBuffer(), output)
        val prediction = SignModelOutput(output.first().toList()).topPrediction()

        return SignInferenceResult(
            gloss =
                prediction?.let { result ->
                    model.labelMap.glossFor(result.classIndex)
                } ?: SignModelContract.UNKNOWN_GLOSS,
            confidence = prediction?.confidence ?: MIN_CONFIDENCE,
        )
    }

    override fun close() {
        if (isClosed) {
            return
        }
        isClosed = true
        model.interpreter.close()
    }

    companion object {
        fun create(
            context: Context,
            variant: SignModelVariant = SignModelVariant.DEFAULT,
            modelAssetPath: String = variant.modelAssetPath,
            labelMapAssetPath: String = SignModelContract.LABEL_MAP_ASSET_PATH,
        ): TfliteSignInferenceAdapter =
            TfliteSignInferenceAdapter(
                model =
                    TfliteSignModelLoader(
                        context = context.applicationContext,
                        modelAssetPath = modelAssetPath,
                        labelMapAssetPath = labelMapAssetPath,
                    ).load(),
            )

        fun createOrFallback(context: Context): SignInferenceAdapter =
            SignInferenceAdapterFactory(context).create()

        private const val TAG = "SignPipeline"
        private const val MIN_CONFIDENCE = 0f
    }
}

private fun FloatArray.toInputBuffer(): ByteBuffer {
    val inputBuffer =
        ByteBuffer
            .allocateDirect(size * SignModelContract.FLOAT_BYTE_SIZE)
            .order(ByteOrder.nativeOrder())
    forEach { value -> inputBuffer.putFloat(value) }
    return inputBuffer.apply { rewind() }
}

private fun Interpreter.validateModelContract() {
    val inputTensor = getInputTensor(0)
    val outputTensor = getOutputTensor(0)

    require(inputTensor.dataType() == DataType.FLOAT32) {
        "TFLite input tensor must be FLOAT32."
    }
    require(outputTensor.dataType() == DataType.FLOAT32) {
        "TFLite output tensor must be FLOAT32."
    }
    require(inputTensor.shape().contentEquals(SignModelContract.inputShape)) {
        "TFLite input shape must be ${SignModelContract.inputShape.contentToString()}."
    }
    require(outputTensor.shape().contentEquals(SignModelContract.outputShape)) {
        "TFLite output shape must be ${SignModelContract.outputShape.contentToString()}."
    }
}
