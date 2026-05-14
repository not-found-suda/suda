package com.ssafy.mobile.feature.conversation.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * 부모 수화 변환 요청 DTO
 */
data class SignToSpeechRequest(
    @SerializedName("words") val words: List<String>,
    @SerializedName("locale") val locale: String = "ko-KR",
    @SerializedName("requestTts") val requestTts: Boolean = true,
)

/**
 * 부모 수화 변환 응답 DTO
 */
data class SignToSpeechResponse(
    @SerializedName("originalWords") val originalWords: List<String>,
    @SerializedName("correctedText") val correctedText: String,
    @SerializedName("audioBase64") val audioBase64: String?,
    @SerializedName("audioMimeType") val audioMimeType: String?,
    @SerializedName("corrected") val corrected: Boolean,
)

/**
 * 자녀 발화 변환 응답 DTO
 */
data class SpeechToTextResponse(
    @SerializedName("recognizedText") val recognizedText: String,
    @SerializedName("correctedText") val correctedText: String,
    @SerializedName("corrected") val corrected: Boolean,
    @SerializedName("confidence") val confidence: Double?,
    @SerializedName("locale") val locale: String?,
)

/**
 * 번역 오류 신고 요청 DTO
 */
data class TranslationFeedbackRequest(
    @SerializedName("clientMessageId") val clientMessageId: String,
    @SerializedName("translatedText") val translatedText: String,
    @SerializedName("reason") val reason: String,
)

/**
 * RFC 9457 표준 에러 응답 DTO
 */
data class ApiErrorResponse(
    @SerializedName("type") val type: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("status") val status: Int? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("instance") val instance: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("traceId") val traceId: String? = null,
)
