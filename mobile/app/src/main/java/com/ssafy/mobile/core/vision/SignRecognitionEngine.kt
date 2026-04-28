package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignRecognitionEvent
import com.ssafy.mobile.core.vision.landmark.LandmarkFrameResult
import kotlinx.coroutines.flow.Flow

interface SignRecognitionEngine {
    val events: Flow<SignRecognitionEvent>

    fun start()

    fun submitFrame(frame: LandmarkFrameResult)

    fun stop()
}
