package com.ssafy.mobile.core.audio

/**
 * 텍스트 기반 음성 출력을 위한 인터페이스.
 * 오프라인 상황에서 시스템 TTS를 활용하기 위해 사용됩니다.
 */
interface TtsPlayer {
    /**
     * 입력된 텍스트를 음성으로 출력합니다.
     * @param text 출력할 텍스트
     * @param onComplete 재생 완료 시 콜백
     * @param onError 에러 발생 시 콜백
     */
    fun speak(
        text: String,
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
    )

    /**
     * 재생 중인 음성을 중지합니다.
     */
    fun stop()

    /**
     * 리소스를 해제합니다.
     */
    fun release()
}
