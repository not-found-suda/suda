package com.ssafy.mobile.core.vision.inference

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter

class TfliteSignModelLoader(
    private val context: Context,
    private val modelAssetPath: String = SignModelContract.MODEL_ASSET_PATH,
    private val labelMapAssetPath: String = SignModelContract.LABEL_MAP_ASSET_PATH,
) {
    fun load(): TfliteSignModel {
        val labelMap = SignLabelMap.parse(readAssetText(labelMapAssetPath))
        val options =
            Interpreter
                .Options()
                .setNumThreads(DEFAULT_THREAD_COUNT)
        val interpreter =
            Interpreter(
                readDirectAssetBuffer(modelAssetPath),
                options,
            )
        return TfliteSignModel(
            interpreter = interpreter,
            labelMap = labelMap,
        )
    }

    private fun readDirectAssetBuffer(assetPath: String): ByteBuffer {
        val bytes =
            context.assets
                .open(assetPath)
                .use { inputStream -> inputStream.readBytes() }

        return ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { rewind() }
    }

    private fun readAssetText(assetPath: String): String =
        context.assets
            .open(assetPath)
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> reader.readText() }

    private companion object {
        const val DEFAULT_THREAD_COUNT = 2
    }
}

class TfliteSignModel(
    val interpreter: Interpreter,
    val labelMap: SignLabelMap,
)
