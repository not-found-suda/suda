package com.ssafy.mobile.core.stt

import kotlinx.coroutines.flow.Flow

/**
 * 음성 인식을 위한 공통 인터페이스.
 */
interface SttEngine {
    /** STT 엔진에서 방출되는 이벤트 스트림 */
    val events: Flow<SttEvent>

    fun nextSessionId(): Int

    /** 음성 인식 시작 */
    fun startListening(sessionId: Int)

    /** 음성 인식 중지 */
    fun stopListening()

    /** 리소스를 해제합니다. */
    fun release()
}
