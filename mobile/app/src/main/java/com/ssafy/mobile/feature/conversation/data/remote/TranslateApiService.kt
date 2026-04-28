package com.ssafy.mobile.feature.conversation.data.remote

import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechRequest
import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.SpeechToTextResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * 번역 및 음성 변환 관련 Spring API 인터페이스
 */
interface TranslateApiService {
    /**
     * 부모 수화 단어열을 문장으로 보정하고 TTS 생성
     */
    @POST("v1/translation/sign-to-speech")
    suspend fun translateSignToSpeech(
        @Body request: SignToSpeechRequest,
        @Query("dryRun") dryRun: Boolean = false,
    ): Response<SignToSpeechResponse>

    /**
     * 자녀 발화 음성을 텍스트로 변환 및 문맥 보정
     */
    @Multipart
    @POST("v1/translation/speech-to-text")
    suspend fun translateSpeechToText(
        @Part audioFile: MultipartBody.Part,
        @Part("locale") locale: String? = "ko-KR",
        @Part("audioMimeType") audioMimeType: String? = null,
        @Query("dryRun") dryRun: Boolean = false,
    ): Response<SpeechToTextResponse>
}
