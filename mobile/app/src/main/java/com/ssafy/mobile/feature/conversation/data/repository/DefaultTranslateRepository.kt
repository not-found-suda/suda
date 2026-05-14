package com.ssafy.mobile.feature.conversation.data.repository

import com.google.gson.Gson
import com.ssafy.mobile.core.audio.WavFileHeader
import com.ssafy.mobile.feature.conversation.data.remote.TranslateApiService
import com.ssafy.mobile.feature.conversation.data.remote.model.ApiErrorResponse
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
import retrofit2.Response

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
                    throw response.toApiException()
                }
            }

        override suspend fun translateSpeechToText(
            audioFile: File,
            mimeType: String,
        ): Result<SpeechToTextResponse> =
            runCatching {
                validateSpeechAudioFile(audioFile)

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
                    throw response.toApiException()
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
                    throw response.toApiException()
                }
            }

        private fun validateSpeechAudioFile(audioFile: File) {
            val validationError = audioFile.getSpeechAudioFileValidationError() ?: return

            throw IOException(validationError)
        }

        private fun File.getSpeechAudioFileValidationError(): String? {
            val fileSize = length()
            return when {
                !exists() || !isFile ->
                    "Invalid STT audio file: path=$absolutePath, " +
                        "exists=${exists()}, size=$fileSize"

                fileSize <= WavFileHeader.HEADER_SIZE_BYTES ->
                    "Invalid STT audio file: path=$absolutePath, " +
                        "size=$fileSize, reason=wav_payload_empty"

                !hasWavHeader() ->
                    "Invalid STT audio file: path=$absolutePath, " +
                        "size=$fileSize, reason=invalid_wav_header"

                else -> null
            }
        }

        private fun File.hasWavHeader(): Boolean =
            inputStream().use { input ->
                val header = ByteArray(WAV_MAGIC_HEADER_SIZE)
                val bytesRead = input.read(header)
                bytesRead >= WAV_MAGIC_HEADER_SIZE &&
                    header.matchesMagicHeader(RIFF_HEADER, RIFF_HEADER_OFFSET) &&
                    header.matchesMagicHeader(WAVE_HEADER, WAVE_HEADER_OFFSET)
            }

        private fun ByteArray.matchesMagicHeader(
            magicHeader: ByteArray,
            offset: Int,
        ): Boolean = copyOfRange(offset, offset + magicHeader.size).contentEquals(magicHeader)

        private fun Response<*>.toApiException(): IOException {
            val errorBody = errorBody()?.string().orEmpty()
            val problemDetails = errorBody.parseProblemDetails()
            val diagnostic =
                problemDetails
                    ?.toDiagnosticMessage()
                    ?.takeIf { it.isNotBlank() }
                    ?: "body=${errorBody.take(ERROR_BODY_PREVIEW_LENGTH)}"

            return IOException(
                "$ERROR_API_PREFIX status=${code()}, message=${message()}, $diagnostic",
            )
        }

        private fun String.parseProblemDetails(): ApiErrorResponse? {
            if (isBlank()) return null

            return runCatching {
                gson.fromJson(this, ApiErrorResponse::class.java)
            }.getOrNull()
        }

        private fun ApiErrorResponse.toDiagnosticMessage(): String =
            listOfNotNull(
                type?.let { "type=$it" },
                title?.let { "title=$it" },
                status?.let { "status=$it" },
                detail?.let { "detail=$it" },
                instance?.let { "instance=$it" },
                code?.let { "code=$it" },
                traceId?.let { "traceId=$it" },
            ).joinToString()

        companion object {
            private const val ERROR_EMPTY_BODY = "Empty response body"
            private const val ERROR_API_PREFIX = "API Error:"
            private const val ERROR_BODY_PREVIEW_LENGTH = 300
            private const val DEFAULT_LOCALE = "ko-KR"
            private const val WAV_MAGIC_HEADER_SIZE = 12
            private const val RIFF_HEADER_OFFSET = 0
            private const val WAVE_HEADER_OFFSET = 8
            private val RIFF_HEADER =
                byteArrayOf(
                    'R'.code.toByte(),
                    'I'.code.toByte(),
                    'F'.code.toByte(),
                    'F'.code.toByte(),
                )
            private val WAVE_HEADER =
                byteArrayOf(
                    'W'.code.toByte(),
                    'A'.code.toByte(),
                    'V'.code.toByte(),
                    'E'.code.toByte(),
                )
            private val gson = Gson()
        }
    }

private const val TEXT_PLAIN = "text/plain"

private fun String.toPlainTextRequestBody() = toRequestBody(TEXT_PLAIN.toMediaTypeOrNull())
