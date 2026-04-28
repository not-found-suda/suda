package com.ssafy.mobile.core.audio

/**
 * 오디오 재생을 위한 인터페이스.
 */
interface AudioPlayer {
    /**
     * 바이트 배열 형태의 오디오 데이터를 재생합니다.
     * @param audioData 재생할 오디오 바이너리 데이터
     * @param onComplete 재생 완료 시 실행할 콜백
     */
    fun play(
        audioData: ByteArray,
        onComplete: () -> Unit = {},
    )

    /**
     * 재생 중인 오디오를 중지합니다.
     */
    fun stop()

    /**
     * 리소스를 해제합니다.
     */
    fun release()
}
