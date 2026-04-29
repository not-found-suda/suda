package com.ssafy.mobile.core.stt

/**
 * STT 엔진에서 발생하는 이벤트들을 정의합니다.
 */
sealed class SttEvent {
    /** 음성 인식 시작 준비 완료 */
    data object Started : SttEvent()

    /** 실시간 인식 중인 텍스트 (확정 전) */
    data class PartialResults(
        val text: String,
    ) : SttEvent()

    /** 최종 인식 결과 텍스트 (확정) */
    data class Results(
        val text: String,
    ) : SttEvent()

    /** 인식 중 발생한 에러 */
    data class Error(
        val message: String,
    ) : SttEvent()

    /** 마이크 입력 음량 변경 (데시벨 단위) */
    data class VolumeChanged(
        val db: Float,
    ) : SttEvent()

    /** 음성 인식 중지 및 리소스 해제 완료 */
    data object Stopped : SttEvent()
}
