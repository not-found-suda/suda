package com.ssafy.mobile.core.stt

/**
 * STT 엔진에서 발생하는 이벤트들을 정의합니다.
 */
sealed class SttEvent(
    val sessionId: Int,
) {
    /** 음성 인식 시작 준비 완료 */
    class Started(
        sessionId: Int,
    ) : SttEvent(sessionId)

    /** 실시간 인식 중인 텍스트 (확정 전) */
    class PartialResults(
        sessionId: Int,
        val text: String,
    ) : SttEvent(sessionId)

    /** 최종 인식 결과 텍스트 (확정) */
    class Results(
        sessionId: Int,
        val text: String,
    ) : SttEvent(sessionId)

    /** 인식 중 발생한 에러 */
    class Error(
        sessionId: Int,
        val type: SttErrorType,
        val message: String,
    ) : SttEvent(sessionId)

    /** 마이크 입력 음량 변경 (데시벨 단위) */
    class VolumeChanged(
        sessionId: Int,
        val db: Float,
    ) : SttEvent(sessionId)

    /** 음성 검출 종료 (결과 대기 중) */
    class EndOfSpeech(
        sessionId: Int,
    ) : SttEvent(sessionId)

    /** 음성 인식 중지 및 리소스 해제 완료 */
    class Stopped(
        sessionId: Int,
    ) : SttEvent(sessionId)
}

enum class SttErrorType {
    RecognizerUnavailable,
    PermissionRequired,
    StartFailed,
    SpeechTimeout,
    NoMatch,
    RecognizerBusy,
    Audio,
    Network,
    Unknown,
}
