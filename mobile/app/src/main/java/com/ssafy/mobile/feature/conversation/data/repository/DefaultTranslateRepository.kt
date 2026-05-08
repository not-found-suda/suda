package com.ssafy.mobile.feature.conversation.data.repository

import com.ssafy.mobile.feature.conversation.data.remote.TranslateApiService
import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechRequest
import com.ssafy.mobile.feature.conversation.data.remote.model.SignToSpeechResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.SpeechToTextResponse
import com.ssafy.mobile.feature.conversation.data.remote.model.TranslationFeedbackRequest
import com.ssafy.mobile.feature.conversation.domain.model.ChatMessage
import com.ssafy.mobile.feature.conversation.domain.model.TranslationFeedbackReason
import com.ssafy.mobile.feature.conversation.domain.repository.TranslateRepository
import java.io.File
import java.io.IOException
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * TranslateRepository의 실제 구현체
 */
class DefaultTranslateRepository
    @Inject
    constructor(
        private val apiService: TranslateApiService,
    ) : TranslateRepository {
        override suspend fun translateSignToSpeech(
            words: List<String>,
        ): Result<SignToSpeechResponse> =
            runCatching {
                val response =
                    apiService.translateSignToSpeech(
                        request = SignToSpeechRequest(words = words),
                    )
                if (response.isSuccessful) {
                    response.body() ?: throw IllegalStateException(ERROR_EMPTY_BODY)
                } else {
                    // 에러 응답 처리 (추후 ApiErrorResponse 파싱 로직 추가 가능)
                    throw IOException("$ERROR_API_PREFIX ${response.code()} ${response.message()}")
                }
            }

        override suspend fun translateSpeechToText(
            audioFile: File,
            mimeType: String,
        ): Result<SpeechToTextResponse> =
            runCatching {
                val requestFile = audioFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val body =
                    MultipartBody.Part.createFormData(
                        "audioFile",
                        audioFile.name,
                        requestFile,
                    )

                val response =
                    apiService.translateSpeechToText(
                        audioFile = body,
                        locale = DEFAULT_LOCALE.toPlainTextRequestBody(),
                        audioMimeType = mimeType.toPlainTextRequestBody(),
                    )

                if (response.isSuccessful) {
                    response.body() ?: throw IllegalStateException(ERROR_EMPTY_BODY)
                } else {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    throw IOException(
                        "$ERROR_API_PREFIX ${response.code()} ${response.message()} " +
                            errorBody.take(ERROR_BODY_PREVIEW_LENGTH),
                    )
                }
            }

        override suspend fun submitTranslationFeedback(
            message: ChatMessage,
            reason: TranslationFeedbackReason,
        ): Result<Unit> =
            runCatching {
                val response =
                    apiService.submitTranslationFeedback(
                        request =
                            TranslationFeedbackRequest(
                                clientMessageId = message.id,
                                translatedText = message.text,
                                reason = reason.name,
                            ),
                    )

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    throw IOException(
                        "$ERROR_API_PREFIX ${response.code()} ${response.message()} " +
                            errorBody.take(ERROR_BODY_PREVIEW_LENGTH),
                    )
                }
            }

        companion object {
            private const val ERROR_EMPTY_BODY = "Empty response body"
            private const val ERROR_API_PREFIX = "API Error:"
            private const val ERROR_BODY_PREVIEW_LENGTH = 300
            private const val DEFAULT_LOCALE = "ko-KR"
        }
    }

private const val TEXT_PLAIN = "text/plain"

private fun String.toPlainTextRequestBody() = toRequestBody(TEXT_PLAIN.toMediaTypeOrNull())
