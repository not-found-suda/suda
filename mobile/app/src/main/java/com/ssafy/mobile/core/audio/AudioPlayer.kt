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
     * Base64 인코딩된 오디오 데이터를 재생합니다.
     * @param base64Data 재생할 Base64 데이터
     * @param onComplete 재생 완료 시 실행할 콜백
     * @param onError 에러 발생 시 실행할 콜백
     */
    fun playBase64(
        base64Data: String,
        onComplete: () -> Unit = {},
        onError: () -> Unit = {},
    )

    /**
     * URL 주소의 오디오 파일을 재생합니다.
     * @param url 재생할 오디오 파일의 URL
     * @param onPrepared 재생 준비가 완료되었을 때 실행할 콜백 (Loading -> Playing 전환용)
     * @param onComplete 재생이 완료되었을 때 실행할 콜백
     * @param onError 에러 발생 시 실행할 콜백
     */
    fun playUrl(
        url: String,
        onPrepared: () -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {},
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
