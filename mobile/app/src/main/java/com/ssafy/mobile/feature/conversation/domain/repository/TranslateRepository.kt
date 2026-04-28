package com.ssafy.mobile.feature.conversation.domain.repository

import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.SpeechToTextResponse
import java.io.File

/**
 * 번역 및 음성 처리 도메인 Repository 인터페이스
 */
interface TranslateRepository {
    /**
     * 부모 수화 단어열을 문장으로 변환하고 TTS 결과 수신
     */
    suspend fun translateSignToSpeech(words: List<String>): Result<SignToSpeechResponse>

    /**
     * 자녀 발화 음성 파일을 텍스트로 변환
     */
    suspend fun translateSpeechToText(
        audioFile: File,
        mimeType: String,
    ): Result<SpeechToTextResponse>
}
