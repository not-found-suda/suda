package com.ssafy.mobile.feature.sign.presentation

import androidx.compose.ui.unit.IntSize

data class CameraAnalysisSettings(
    val targetResolution: IntSize = IntSize(DEFAULT_TARGET_WIDTH, DEFAULT_TARGET_HEIGHT),
    val targetFps: Int = DEFAULT_TARGET_FPS,
    val analysisFrameInterval: Int = DEFAULT_ANALYSIS_FRAME_INTERVAL,
    val mirrorAnalysisInput: Boolean = DEFAULT_MIRROR_ANALYSIS_INPUT,
) {
    init {
        require(targetResolution.width > 0 && targetResolution.height > 0) {
            "Target resolution must be positive."
        }
        require(targetFps > 0) {
            "Target FPS must be positive."
        }
        require(analysisFrameInterval > 0) {
            "Analysis frame interval must be positive."
        }
    }

    companion object {
        const val DEFAULT_TARGET_WIDTH = 960
        const val DEFAULT_TARGET_HEIGHT = 540
        const val DEFAULT_TARGET_FPS = 15
        const val DEFAULT_ANALYSIS_FRAME_INTERVAL = 1
        const val DEFAULT_MIRROR_ANALYSIS_INPUT = false
    }
}

data class CameraPerformanceMetrics(
    val frameCount: Long? = null,
    val cameraFps: Double? = null,
    val mediaPipeMs: Double? = null,
    val pipelineLatencyMs: Double? = null,
    val analysisImageSize: IntSize? = null,
)
