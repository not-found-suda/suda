package com.ssafy.mobile.translation

import android.content.Context
import android.util.Log
import com.ssafy.mobile.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object OnDeviceModelDebugBootstrapper {
    fun prepareBundledDebugModel(context: Context) {
        if (!BuildConfig.DEBUG) return

        Thread {
            runCatching {
                copyBundledModelIfNeeded(context.applicationContext)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to prepare bundled debug model.", exception)
            }
        }.apply {
            name = "DebugModelBootstrapper"
            isDaemon = true
            start()
        }
    }

    private fun copyBundledModelIfNeeded(context: Context) {
        val targetFile = File(context.filesDir, OnDeviceModelConfig.QWEN_MODEL_FILE_NAME)
        if (targetFile.isFile && targetFile.length() > MIN_VALID_MODEL_SIZE_BYTES) return

        if (!hasBundledModelAsset(context)) return

        targetFile.parentFile?.mkdirs()
        context.assets.open(OnDeviceModelConfig.QWEN_MODEL_ASSET_PATH).use { input ->
            FileOutputStream(targetFile, false).use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
        Log.i(TAG, "Bundled debug model prepared: ${targetFile.absolutePath}")
    }

    private fun hasBundledModelAsset(context: Context): Boolean =
        try {
            context.assets.open(OnDeviceModelConfig.QWEN_MODEL_ASSET_PATH).close()
            true
        } catch (_: IOException) {
            false
        }

    private const val TAG = "OnDeviceModelDebug"
    private const val BUFFER_SIZE = 1024 * 1024
    private const val MIN_VALID_MODEL_SIZE_BYTES = 1024L * 1024L
}
