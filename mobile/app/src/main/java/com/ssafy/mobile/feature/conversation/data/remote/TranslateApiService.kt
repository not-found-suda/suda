package com.ssafy.mobile.feature.conversation.data.remote

import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechRequest
import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.SpeechToTextResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.TranslationFeedbackRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
        @Part("sessionId") sessionId: RequestBody? = null,
        @Part("locale") locale: RequestBody,
        @Part("audioMimeType") audioMimeType: RequestBody,
        @Query("dryRun") dryRun: Boolean = false,
    ): Response<SpeechToTextResponse>

    /**
     * 번역 결과 오류 신고
     */
    @POST("v1/translation/feedback")
    suspend fun submitTranslationFeedback(
        @Body request: TranslationFeedbackRequest,
    ): Response<Unit>
}
