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
 * RFC 9457 표준 에러 응답 DTO
 */
data class ApiErrorResponse(
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("status") val status: Int,
    @SerializedName("detail") val detail: String,
    @SerializedName("instance") val instance: String,
    @SerializedName("code") val code: String,
    @SerializedName("traceId") val traceId: String?,
)
