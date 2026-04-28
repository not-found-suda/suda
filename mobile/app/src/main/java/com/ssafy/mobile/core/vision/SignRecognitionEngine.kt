package com.ssafy.mobile.core.vision

import com.ssafy.mobile.core.model.SignRecognitionEvent
import kotlinx.coroutines.flow.Flow

/**
 * A 파트(수어 인식 파이프라인)가 B 파트(UI/통신)에게 제공하는 유일한 소통 창구.
 * A 파트의 내부 구현(CameraX, MediaPipe, TFLite)을 몰라도 B는 이 인터페이스만으로 개발 가능.
 *
 * 구현체: RealSignRecognitionEngine (A 파트 구현)
 */
interface SignRecognitionEngine {
    /** B 파트가 구독할 수어 인식 이벤트 스트림 */
    val events: Flow<SignRecognitionEvent>

    /** 카메라 및 AI 파이프라인 시작 */
    fun start()

    /** 카메라 및 AI 파이프라인 중지 및 리소스 해제 */
    fun stop()
}
